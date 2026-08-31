package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.AttachmentValidationService;
import com.prabhix.platform.mail.domain.MailAttachment;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadFlag;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.inbound.MimeParser;
import com.prabhix.platform.mail.outbound.MailDispatcher;
import com.prabhix.platform.mail.outbound.SuppressionService;
import com.prabhix.platform.mail.repository.MailAttachmentRepository;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.mail.repository.MailThreadFlagRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.mail.util.MailSubjectUtil;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Sends a message that is not a reply to anything.
 *
 * <p>{@code ReplyService} could only answer mail that had already arrived, which is all a helpdesk needs
 * and half of what a mailbox is. This starts a thread from nothing: it makes the thread, the outbound
 * message and the outbox row, files the thread in Sent, and marks it read for its author — who has
 * obviously read what they just wrote.
 */
@Service
@RequiredArgsConstructor
public class ComposeService {

    private static final int MAX_RECIPIENTS = 100;

    private final MailThreadRepository threadRepository;
    private final MailMessageRepository messageRepository;
    private final MailAttachmentRepository attachmentRepository;
    private final MailThreadFlagRepository flagRepository;
    private final MailDispatcher mailDispatcher;
    private final MailFolderService folders;
    private final MailDraftService drafts;
    private final MailboxAccess access;
    private final AttachmentValidationService attachmentValidation;
    private final SuppressionService suppressions;
    private final PrabhixProperties properties;

    @Transactional
    public MailboxDtos.ComposeResponse send(PrabhixPrincipal principal,
                                            MailboxDtos.ComposeRequest request) {
        UUID orgId = principal.requireOrganizationId();
        Mailbox mailbox = access.requireMailbox(principal, request.mailboxId());

        List<String> to = recipients(request.to(), orgId, true);
        List<String> cc = recipients(request.cc(), orgId, false);
        List<String> bcc = recipients(request.bcc(), orgId, false);
        if (to.isEmpty()) {
            // Every address given was suppressed. Queueing this would produce a message that silently
            // goes nowhere, which is the failure mode this whole subsystem exists to avoid.
            throw ApiException.invalidState(
                    "None of those addresses can be delivered to. They have bounced or unsubscribed.");
        }
        if (to.size() + cc.size() + bcc.size() > MAX_RECIPIENTS) {
            throw ApiException.invalidState("A message can go to at most " + MAX_RECIPIENTS + " people");
        }

        List<StoredFile> attachments = attachmentValidation.requireCleanAttachments(
                orgId, request.attachmentIds() != null ? request.attachmentIds() : List.of());

        String subject = request.subject() != null && !request.subject().isBlank()
                ? request.subject().trim() : "(no subject)";

        MailThread thread = new MailThread();
        thread.setOrganizationId(orgId);
        thread.setMailboxId(mailbox.getId());
        thread.setReferenceKey(Ids.readableCode(6));
        thread.setSubject(subject);
        thread.setNormalizedSubject(MailSubjectUtil.normalize(subject));
        thread.setCustomerEmail(to.get(0));
        thread.setParticipantEmails(Json.toJson(participants(mailbox, to, cc, bcc)));
        thread.setLastMessageAt(Instant.now());
        thread.setLastMessageDirection(MailEnums.MessageDirection.OUTBOUND);
        thread.setMessageCount(1);
        thread.setHasAttachments(!attachments.isEmpty());
        // Assigned to whoever wrote it: they started the conversation, so the reply belongs to them.
        thread.setAssigneeUserId(principal.userId());
        thread.setAssignedAt(Instant.now());
        thread.setAssignedBy(principal.userId());
        thread = threadRepository.save(thread);

        String taggedSubject = MailSubjectUtil.injectToken(
                subject, properties.mail().threading().tokenPrefix(), thread.getReferenceKey());

        String bodyHtml = request.bodyHtml() != null ? request.bodyHtml() : "";
        if (mailbox.getSignatureHtml() != null && !mailbox.getSignatureHtml().isBlank()) {
            bodyHtml = bodyHtml + "<br><br>" + mailbox.getSignatureHtml();
        }

        MailMessage outbound = new MailMessage();
        outbound.setOrganizationId(orgId);
        outbound.setThreadId(thread.getId());
        outbound.setMailboxId(mailbox.getId());
        outbound.setDirection(MailEnums.MessageDirection.OUTBOUND);
        outbound.setMessageIdHeader("<" + UUID.randomUUID() + "@prabhix>");
        outbound.setFromAddress(mailbox.getAddress());
        outbound.setFromName(mailbox.getName());
        outbound.setToAddresses(Json.toJson(to));
        outbound.setCcAddresses(Json.toJson(cc));
        outbound.setSubject(taggedSubject);
        outbound.setBodyHtml(bodyHtml);
        outbound.setBodyText(MimeParser.htmlToText(bodyHtml));
        outbound.setDeliveryStatus(MailEnums.DeliveryStatus.QUEUED);
        outbound.setSentByUserId(principal.userId());
        outbound.setOccurredAt(Instant.now());
        outbound.setAttachmentCount(attachments.size());
        outbound = messageRepository.save(outbound);

        for (StoredFile file : attachments) {
            MailAttachment attachment = new MailAttachment();
            attachment.setOrganizationId(orgId);
            attachment.setMessageId(outbound.getId());
            attachment.setFileId(file.getId());
            attachment.setFilename(file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setSizeBytes(file.getSizeBytes());
            attachmentRepository.save(attachment);
        }

        thread.setSnippet(outbound.getBodyText());
        threadRepository.save(thread);

        MailOutbox outbox = new MailOutbox();
        outbox.setOrganizationId(orgId);
        outbox.setMailboxId(mailbox.getId());
        outbox.setThreadId(thread.getId());
        outbox.setMessageId(outbound.getId());
        outbox.setFromAddress(mailbox.getAddress());
        outbox.setFromName(mailbox.getName());
        outbox.setReplyTo(mailbox.getReplyTo() != null ? mailbox.getReplyTo() : mailbox.getAddress());
        outbox.setToAddresses(Json.toJson(to));
        outbox.setCcAddresses(Json.toJson(cc));
        outbox.setBccAddresses(Json.toJson(bcc));
        outbox.setSubject(taggedSubject);
        outbox.setBodyHtml(bodyHtml);
        outbox.setBodyText(outbound.getBodyText());
        outbox.setAttachmentIds(Json.toJson(
                attachments.stream().map(f -> f.getId().toString()).toList()));
        outbox.setPriority(10);
        mailDispatcher.enqueueDirect(outbox);

        folders.fileNewThread(thread, MailEnums.FolderKind.SENT);

        MailThreadFlag flag = MailThreadFlag.of(thread.getId(), principal.userId(), orgId);
        flag.setReadAt(Instant.now());
        flagRepository.save(flag);

        drafts.discardQuietly(principal, request.draftId());

        return new MailboxDtos.ComposeResponse(thread.getId(), outbound.getId(), taggedSubject);
    }

    /**
     * Normalizes and de-duplicates addresses, dropping any that are suppressed.
     *
     * <p>Suppression is checked here rather than at send time because a person composing a message can be
     * told that an address has bounced, and the outbox worker can only drop it silently hours later.
     */
    private List<String> recipients(List<String> raw, UUID orgId, boolean required) {
        if (raw == null || raw.isEmpty()) {
            if (required) {
                throw ApiException.invalidState("Add at least one recipient");
            }
            return List.of();
        }
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String address : raw) {
            if (address == null || address.isBlank()) {
                continue;
            }
            String normalized = address.trim().toLowerCase(Locale.ROOT);
            if (suppressions.isSuppressed(normalized, orgId)) {
                continue;
            }
            clean.add(normalized);
        }
        return List.copyOf(clean);
    }

    private Set<String> participants(Mailbox mailbox, List<String> to, List<String> cc, List<String> bcc) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        all.add(mailbox.getAddress().toLowerCase(Locale.ROOT));
        all.addAll(to);
        all.addAll(cc);
        all.addAll(bcc);
        return all;
    }
}
