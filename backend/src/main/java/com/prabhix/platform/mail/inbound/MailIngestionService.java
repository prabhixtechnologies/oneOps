package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.mail.domain.*;
import com.prabhix.platform.mail.event.MailMessageReceivedEvent;
import com.prabhix.platform.mail.event.MailStreamEvent;
import com.prabhix.platform.mail.helpdesk.SlaService;
import com.prabhix.platform.mail.outbound.SuppressionService;
import com.prabhix.platform.mail.repository.*;
import com.prabhix.platform.mail.util.MailJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailIngestionService {

    private static final int INLINE_RAW_MAX = 64 * 1024;
    private static final String INLINE_RAW_PREFIX = "b64:";

    private final MailInboundRawRepository inboundRawRepository;
    private final MailMessageRepository messageRepository;
    private final MailAttachmentRepository attachmentRepository;
    private final MailThreadRepository threadRepository;
    private final MailThreadEventRepository eventRepository;
    private final MailboxRepository mailboxRepository;
    private final MailTagRepository tagRepository;
    private final MailThreadTagRepository threadTagRepository;
    private final FileStorageService fileStorageService;
    private final MimeParser mimeParser;
    private final ThreadResolver threadResolver;
    private final RoutingRuleEngine routingRuleEngine;
    private final SlaService slaService;
    private final SuppressionService suppressionService;
    private final ApplicationEventPublisher events;

    @Scheduled(fixedDelayString = "${prabhix.mail.inbound.poll-interval}")
    @Transactional
    public void processPending() {
        var batch = inboundRawRepository.claimPending(20);
        for (MailInboundRaw raw : batch) {
            raw.setStatus(MailEnums.InboundRawStatus.PROCESSING);
            raw.setAttempts(raw.getAttempts() + 1);
            inboundRawRepository.save(raw);
            try {
                processOne(raw);
            } catch (Exception ex) {
                log.warn("Inbound raw {} failed: {}", raw.getId(), ex.getMessage());
                raw.setStatus(MailEnums.InboundRawStatus.FAILED);
                raw.setLastError(ex.getMessage());
                inboundRawRepository.save(raw);
            }
        }
    }

    void processOne(MailInboundRaw raw) throws Exception {
        if (raw.getStatus() == MailEnums.InboundRawStatus.PROCESSED
                && raw.getResultingMessageId() != null) {
            return;
        }

        byte[] bytes = loadRawBytes(raw);
        MimeParser.ParsedMime parsed = mimeParser.parse(bytes);

        if (isDsn(bytes, parsed)) {
            processDsn(raw, bytes, parsed);
            return;
        }

        if (parsed.getMessageIdHeader() != null) {
            var existing = messageRepository.findByMailboxIdAndMessageIdHeader(
                    raw.getMailboxId(), parsed.getMessageIdHeader());
            if (existing.isPresent()) {
                raw.setStatus(MailEnums.InboundRawStatus.SKIPPED_DUPLICATE);
                raw.setProcessedAt(Instant.now());
                inboundRawRepository.save(raw);
                return;
            }
        }

        MailThread thread = threadResolver.resolve(raw.getOrganizationId(), raw.getMailboxId(), parsed);

        RoutingRuleEngine.RoutingContext routingContext = new RoutingRuleEngine.RoutingContext();
        routingRuleEngine.apply(raw.getOrganizationId(), raw.getMailboxId(), thread, parsed, routingContext);

        MailMessage message = buildMessage(raw, parsed, thread);
        try {
            message = messageRepository.save(message);
        } catch (DataIntegrityViolationException ex) {
            raw.setStatus(MailEnums.InboundRawStatus.SKIPPED_DUPLICATE);
            raw.setProcessedAt(Instant.now());
            inboundRawRepository.save(raw);
            return;
        }

        storeAttachments(raw, message, parsed);

        updateThread(thread, parsed, message, routingContext);
        slaService.onInboundMessage(thread, parsed);

        appendEvent(thread, message);
        applyTags(thread, raw.getOrganizationId(), routingContext);

        raw.setStatus(MailEnums.InboundRawStatus.PROCESSED);
        raw.setResultingMessageId(message.getId());
        raw.setProcessedAt(Instant.now());
        raw.setMessageIdHeader(parsed.getMessageIdHeader());
        inboundRawRepository.save(raw);

        events.publishEvent(new MailMessageReceivedEvent(
                raw.getOrganizationId(), raw.getMailboxId(), thread.getId(), message.getId()));
        events.publishEvent(new MailStreamEvent("new-message", raw.getOrganizationId(),
                Map.of("threadId", thread.getId(), "messageId", message.getId())));
    }

    private byte[] loadRawBytes(MailInboundRaw raw) {
        if (raw.getRawContent() != null) {
            String content = raw.getRawContent();
            if (content.startsWith(INLINE_RAW_PREFIX)) {
                return java.util.Base64.getDecoder().decode(content.substring(INLINE_RAW_PREFIX.length()));
            }
            return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (raw.getRawFileId() != null) {
            // Ingestion runs on a scheduler with no tenant context, so the organization has to
            // come from the row rather than TenantContext.
            return fileStorageService.read(raw.getOrganizationId(), raw.getRawFileId());
        }
        throw new IllegalStateException("Inbound raw has no content");
    }

    private MailMessage buildMessage(MailInboundRaw raw, MimeParser.ParsedMime parsed, MailThread thread) {
        MailMessage message = new MailMessage();
        message.setOrganizationId(raw.getOrganizationId());
        message.setThreadId(thread.getId());
        message.setMailboxId(raw.getMailboxId());
        message.setDirection(MailEnums.MessageDirection.INBOUND);
        message.setMessageIdHeader(parsed.getMessageIdHeader());
        message.setInReplyTo(parsed.getInReplyTo());
        message.setReferencesHeader(parsed.getReferencesHeader());
        message.setFromAddress(parsed.getFrom() != null ? parsed.getFrom() : "unknown@invalid");
        message.setFromName(parsed.getFromName());
        message.setToAddresses(MailJson.toJson(parsed.getToAddresses()));
        message.setCcAddresses(MailJson.toJson(parsed.getCcAddresses()));
        message.setSubject(parsed.getSubject());
        message.setBodyText(parsed.getBodyText());
        message.setBodyHtml(parsed.getBodyHtml());
        message.setSnippet(parsed.getSnippet());
        message.setHeaders(MailJson.toJson(parsed.getHeaders()));
        message.setSizeBytes(parsed.getSizeBytes());
        message.setAttachmentCount(parsed.getAttachmentCount());
        message.setDeliveryStatus(MailEnums.DeliveryStatus.RECEIVED);
        message.setOccurredAt(Instant.now());
        return message;
    }

    private void storeAttachments(MailInboundRaw raw, MailMessage message, MimeParser.ParsedMime parsed) {
        for (MimeParser.AttachmentPart part : parsed.getAttachments()) {
            StoredFile file = fileStorageService.store(
                    part.getContent(), part.getFilename(), part.getContentType(),
                    StoredFile.FilePurpose.MAIL_ATTACHMENT, null);
            MailAttachment attachment = new MailAttachment();
            attachment.setOrganizationId(raw.getOrganizationId());
            attachment.setMessageId(message.getId());
            attachment.setFileId(file.getId());
            attachment.setFilename(part.getFilename());
            attachment.setContentType(part.getContentType());
            attachment.setSizeBytes(part.getContent().length);
            attachment.setInline(part.isInline());
            attachment.setContentId(part.getContentId());
            attachmentRepository.save(attachment);
        }
    }

    private void updateThread(MailThread thread, MimeParser.ParsedMime parsed,
                              MailMessage message, RoutingRuleEngine.RoutingContext ctx) {
        boolean isFirstMessage = thread.getMessageCount() == 0;
        thread.setMessageCount(thread.getMessageCount() + 1);
        thread.setUnreadCount(thread.getUnreadCount() + 1);
        if (parsed.getAttachmentCount() > 0) {
            thread.setHasAttachments(true);
        }
        thread.setSnippet(parsed.getSnippet());
        thread.setLastMessageAt(message.getOccurredAt());
        thread.setLastMessageDirection(MailEnums.MessageDirection.INBOUND);

        Set<String> participants = new HashSet<>(MailJson.parseStringList(thread.getParticipantEmails()));
        if (parsed.getFrom() != null) {
            participants.add(parsed.getFrom().toLowerCase());
        }
        parsed.getToAddresses().forEach(a -> participants.add(a.toLowerCase()));
        parsed.getCcAddresses().forEach(a -> participants.add(a.toLowerCase()));
        thread.setParticipantEmails(MailJson.toJson(participants));

        if (ctx.getSlaPolicyMinutes() != null) {
            thread.setSlaPolicyFirstMins(ctx.getSlaPolicyMinutes());
        }
        threadRepository.save(thread);

        if (isFirstMessage && thread.getStatus() == MailEnums.ThreadStatus.OPEN) {
            mailboxRepository.findById(thread.getMailboxId()).ifPresent(mb -> {
                mb.setOpenThreadCount(mb.getOpenThreadCount() + 1);
                if (thread.getAssigneeUserId() == null && thread.getAssigneeTeamId() == null) {
                    mb.setUnassignedCount(mb.getUnassignedCount() + 1);
                }
                mailboxRepository.save(mb);
            });
        }
    }

    private void appendEvent(MailThread thread, MailMessage message) {
        MailThreadEvent event = new MailThreadEvent();
        event.setOrganizationId(thread.getOrganizationId());
        event.setThreadId(thread.getId());
        event.setEventType(MailEnums.ThreadEventType.MESSAGE_RECEIVED);
        event.setToValue(message.getFromAddress());
        eventRepository.save(event);
    }

    private void applyTags(MailThread thread, UUID orgId, RoutingRuleEngine.RoutingContext ctx) {
        if (ctx.getTagSlugs().isEmpty()) {
            return;
        }
        List<MailTag> tags = tagRepository.findByOrganizationIdAndSlugIn(orgId, ctx.getTagSlugs());
        for (MailTag tag : tags) {
            if (!threadTagRepository.existsByIdThreadIdAndIdTagId(thread.getId(), tag.getId())) {
                MailThreadTag tt = new MailThreadTag();
                tt.setId(new MailThreadTag.Id(thread.getId(), tag.getId()));
                tt.setOrganizationId(orgId);
                threadTagRepository.save(tt);
                tag.setUsageCount(tag.getUsageCount() + 1);
                tagRepository.save(tag);
            }
        }
    }

    @Transactional
    public MailInboundRaw stageRaw(UUID organizationId, UUID mailboxId,
                                   MailEnums.InboundSource source, Long sourceUid,
                                   byte[] rawBytes) {
        MailInboundRaw raw = new MailInboundRaw();
        raw.setOrganizationId(organizationId);
        raw.setMailboxId(mailboxId);
        raw.setSource(source);
        raw.setSourceUid(sourceUid);
        raw.setSizeBytes(rawBytes.length);
        if (rawBytes.length <= INLINE_RAW_MAX) {
            raw.setRawContent(INLINE_RAW_PREFIX
                    + java.util.Base64.getEncoder().encodeToString(rawBytes));
        } else {
            StoredFile file = fileStorageService.store(
                    rawBytes, "message.eml", "message/rfc822",
                    StoredFile.FilePurpose.MAIL_RAW_MIME, null);
            raw.setRawFileId(file.getId());
        }
        raw.setStatus(MailEnums.InboundRawStatus.PENDING);
        raw.setReceivedAt(Instant.now());
        return inboundRawRepository.save(raw);
    }

    private boolean isDsn(byte[] bytes, MimeParser.ParsedMime parsed) {
        try {
            Session session = Session.getInstance(new Properties());
            MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(bytes));
            return DsnDetector.isDeliveryStatusNotification(message, parsed);
        } catch (Exception ex) {
            return false;
        }
    }

    private void processDsn(MailInboundRaw raw, byte[] bytes, MimeParser.ParsedMime parsed) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(bytes));
        String reportBody = DsnDetector.extractReportBody(message);
        if (reportBody.isBlank() && parsed.getBodyText() != null) {
            reportBody = parsed.getBodyText();
        }
        suppressionService.parseAndRecordDsn(reportBody, raw.getOrganizationId());
        raw.setStatus(MailEnums.InboundRawStatus.PROCESSED);
        raw.setProcessedAt(Instant.now());
        raw.setMessageIdHeader(parsed.getMessageIdHeader());
        inboundRawRepository.save(raw);
    }
}
