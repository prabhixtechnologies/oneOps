package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.service.AttachmentValidationService;
import com.prabhix.platform.mail.domain.MailAttachment;
import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.outbound.MailDispatcher;
import com.prabhix.platform.mail.repository.*;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.mail.util.MailSubjectUtil;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReplyService {

    private static final DateTimeFormatter FORWARD_DATE =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z").withZone(ZoneId.systemDefault());

    private final MailThreadRepository threadRepository;
    private final MailMessageRepository messageRepository;
    private final MailAttachmentRepository attachmentRepository;
    private final MailboxRepository mailboxRepository;
    private final MailAliasRepository aliasRepository;
    private final StoredFileRepository storedFileRepository;
    private final MailDispatcher mailDispatcher;
    private final AssignmentService assignmentService;
    private final CannedReplyService cannedReplyService;
    private final SlaService slaService;
    private final AttachmentValidationService attachmentValidationService;
    private final PrabhixProperties properties;

    @Transactional
    public ThreadDtos.MessageSummary reply(PrabhixPrincipal principal, UUID threadId,
                                           ThreadDtos.ReplyRequest request) {
        UUID orgId = principal.requireOrganizationId();
        MailThread thread = threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(threadId, orgId)
                .orElseThrow(() -> ApiException.notFound("Thread"));
        Mailbox mailbox = mailboxRepository.findById(thread.getMailboxId())
                .orElseThrow(() -> ApiException.of(com.prabhix.platform.common.error.ErrorCode.MAILBOX_NOT_FOUND,
                        "Mailbox not found"));

        MailEnums.ReplyMode mode = request.replyMode() != null ? request.replyMode() : MailEnums.ReplyMode.REPLY;
        MailMessage source = messageRepository.findFirstByThreadIdAndDeletedAtIsNullOrderByOccurredAtDesc(threadId)
                .orElseThrow(() -> ApiException.invalidState("Thread has no message to reply to"));

        Recipients recipients = resolveRecipients(mode, request, source, mailbox, orgId);
        List<StoredFile> attachments = resolveAttachments(mode, orgId, request, source);

        assignmentService.claimIfUnassigned(thread.getId(), principal.userId());

        String prefix = properties.mail().threading().tokenPrefix();
        String subject = buildSubject(mode, request.subject(), thread, source);
        subject = MailSubjectUtil.injectToken(subject, prefix, thread.getReferenceKey());

        String bodyHtml = buildBodyHtml(mode, request.bodyHtml(), source);
        if (mailbox.getSignatureHtml() != null && !mailbox.getSignatureHtml().isBlank()
                && mode != MailEnums.ReplyMode.FORWARD) {
            bodyHtml = bodyHtml + "<br><br>" + mailbox.getSignatureHtml();
        }

        MailMessage outbound = new MailMessage();
        outbound.setOrganizationId(thread.getOrganizationId());
        outbound.setThreadId(threadId);
        outbound.setMailboxId(thread.getMailboxId());
        outbound.setDirection(MailEnums.MessageDirection.OUTBOUND);
        outbound.setMessageIdHeader("<" + UUID.randomUUID() + "@prabhix>");
        if (mode != MailEnums.ReplyMode.FORWARD) {
            outbound.setInReplyTo(source.getMessageIdHeader());
            outbound.setReferencesHeader(buildReferences(source));
        }
        outbound.setFromAddress(mailbox.getAddress());
        outbound.setToAddresses(Json.toJson(recipients.to()));
        outbound.setCcAddresses(Json.toJson(recipients.cc()));
        outbound.setSubject(subject);
        outbound.setBodyHtml(bodyHtml);
        outbound.setBodyText(com.prabhix.platform.mail.inbound.MimeParser.htmlToText(bodyHtml));
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

        List<String> attachmentIdStrings = attachments.stream()
                .map(f -> f.getId().toString())
                .collect(Collectors.toList());

        MailOutbox outbox = new MailOutbox();
        outbox.setOrganizationId(thread.getOrganizationId());
        outbox.setMailboxId(thread.getMailboxId());
        outbox.setThreadId(threadId);
        outbox.setMessageId(outbound.getId());
        outbox.setFromAddress(mailbox.getAddress());
        outbox.setFromName(mailbox.getName());
        outbox.setReplyTo(mailbox.getReplyTo() != null ? mailbox.getReplyTo() : mailbox.getAddress());
        outbox.setToAddresses(Json.toJson(recipients.to()));
        outbox.setCcAddresses(Json.toJson(recipients.cc()));
        outbox.setSubject(subject);
        outbox.setBodyHtml(bodyHtml);
        outbox.setBodyText(outbound.getBodyText());
        outbox.setHeaders(Json.toJson(buildHeaders(outbound, mode)));
        outbox.setAttachmentIds(Json.toJson(attachmentIdStrings));
        outbox.setPriority(10);
        mailDispatcher.enqueueDirect(outbox);

        thread.setLastMessageAt(Instant.now());
        thread.setLastMessageDirection(MailEnums.MessageDirection.OUTBOUND);
        thread.setMessageCount(thread.getMessageCount() + 1);
        thread.setSnippet(outbound.getBodyText());
        if (!attachments.isEmpty()) {
            thread.setHasAttachments(true);
        }
        threadRepository.save(thread);
        slaService.onOutboundReply(thread);
        cannedReplyService.recordUse(orgId, request.cannedReplyId());

        return new ThreadDtos.MessageSummary(
                outbound.getId(), outbound.getDirection(), outbound.getFromAddress(), null,
                outbound.getSubject(), outbound.getSnippet(), outbound.getBodyText(), outbound.getBodyHtml(),
                outbound.getDeliveryStatus(), outbound.getOccurredAt(), outbound.getAttachmentCount());
    }

    private Recipients resolveRecipients(MailEnums.ReplyMode mode, ThreadDtos.ReplyRequest request,
                                         MailMessage source, Mailbox mailbox, UUID orgId) {
        return switch (mode) {
            case REPLY -> {
                String to = primaryRecipient(source);
                List<String> toList = request.to() != null && !request.to().isEmpty()
                        ? request.to() : List.of(to);
                yield new Recipients(toList, request.cc() != null ? request.cc() : List.of());
            }
            case REPLY_ALL -> {
                String to = primaryRecipient(source);
                List<String> toList = request.to() != null && !request.to().isEmpty()
                        ? request.to() : List.of(to);
                Set<String> ours = ourAddresses(mailbox, orgId);
                LinkedHashSet<String> cc = new LinkedHashSet<>();
                Json.parseStringList(source.getToAddresses()).stream()
                        .map(a -> a.toLowerCase(Locale.ROOT))
                        .filter(a -> !ours.contains(a))
                        .filter(a -> !a.equalsIgnoreCase(to))
                        .forEach(cc::add);
                Json.parseStringList(source.getCcAddresses()).stream()
                        .map(a -> a.toLowerCase(Locale.ROOT))
                        .filter(a -> !ours.contains(a))
                        .filter(a -> !a.equalsIgnoreCase(to))
                        .forEach(cc::add);
                if (request.cc() != null) {
                    request.cc().forEach(a -> cc.add(a.toLowerCase(Locale.ROOT)));
                }
                yield new Recipients(toList, List.copyOf(cc));
            }
            case FORWARD -> {
                if (request.to() == null || request.to().isEmpty()) {
                    throw ApiException.invalidState("Forward requires at least one recipient");
                }
                yield new Recipients(request.to(), request.cc() != null ? request.cc() : List.of());
            }
        };
    }

    private List<StoredFile> resolveAttachments(MailEnums.ReplyMode mode, UUID orgId,
                                                ThreadDtos.ReplyRequest request, MailMessage source) {
        List<StoredFile> uploaded = attachmentValidationService.requireCleanAttachments(
                orgId, request.attachmentIds() != null ? request.attachmentIds() : List.of());
        if (mode != MailEnums.ReplyMode.FORWARD) {
            return uploaded;
        }
        List<StoredFile> forwarded = new ArrayList<>(uploaded);
        for (MailAttachment attachment : attachmentRepository.findByMessageId(source.getId())) {
            if (attachment.isInline()) {
                continue;
            }
            storedFileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(
                    attachment.getFileId(), orgId).ifPresent(forwarded::add);
        }
        return forwarded;
    }

    private String primaryRecipient(MailMessage source) {
        if (source.getReplyToAddress() != null && !source.getReplyToAddress().isBlank()) {
            return source.getReplyToAddress();
        }
        return source.getFromAddress();
    }

    private Set<String> ourAddresses(Mailbox mailbox, UUID orgId) {
        Set<String> ours = new LinkedHashSet<>();
        ours.add(mailbox.getAddress().toLowerCase(Locale.ROOT));
        if (mailbox.getReplyTo() != null) {
            ours.add(mailbox.getReplyTo().toLowerCase(Locale.ROOT));
        }
        aliasRepository.findByMailboxIdAndOrganizationId(mailbox.getId(), orgId).stream()
                .map(a -> a.getAddress().toLowerCase(Locale.ROOT))
                .forEach(ours::add);
        return ours;
    }

    private String buildSubject(MailEnums.ReplyMode mode, String requested, MailThread thread,
                                MailMessage source) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        String base = source.getSubject() != null ? source.getSubject() : thread.getSubject();
        return switch (mode) {
            case FORWARD -> base.toLowerCase(Locale.ROOT).startsWith("fwd:") ? base : "Fwd: " + base;
            default -> base.toLowerCase(Locale.ROOT).startsWith("re:") ? base : "Re: " + base;
        };
    }

    private String buildBodyHtml(MailEnums.ReplyMode mode, String agentBody, MailMessage source) {
        if (mode != MailEnums.ReplyMode.FORWARD) {
            return agentBody;
        }
        String quoted = buildForwardQuote(source);
        return agentBody + "<br><br>" + quoted;
    }

    private String buildForwardQuote(MailMessage source) {
        String from = source.getFromName() != null && !source.getFromName().isBlank()
                ? source.getFromName() + " &lt;" + source.getFromAddress() + "&gt;"
                : source.getFromAddress();
        String to = String.join(", ", Json.parseStringList(source.getToAddresses()));
        String date = FORWARD_DATE.format(source.getOccurredAt());
        String subject = source.getSubject() != null ? source.getSubject() : "";
        String original = source.getBodyHtml() != null && !source.getBodyHtml().isBlank()
                ? source.getBodyHtml()
                : escapeHtml(source.getBodyText());
        return """
                <div style="border-left:2px solid #ccc;padding-left:12px;margin-top:8px">
                <p>---------- Forwarded message ---------<br>
                <b>From:</b> %s<br>
                <b>Date:</b> %s<br>
                <b>Subject:</b> %s<br>
                <b>To:</b> %s</p>
                %s
                </div>
                """.formatted(from, date, escapeHtml(subject), escapeHtml(to), original);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildReferences(MailMessage last) {
        List<String> refs = new ArrayList<>();
        if (last.getReferencesHeader() != null) {
            refs.add(last.getReferencesHeader());
        }
        if (last.getMessageIdHeader() != null) {
            refs.add(last.getMessageIdHeader());
        }
        return String.join(" ", refs);
    }

    private java.util.Map<String, String> buildHeaders(MailMessage msg, MailEnums.ReplyMode mode) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (mode != MailEnums.ReplyMode.FORWARD && msg.getInReplyTo() != null) {
            headers.put("In-Reply-To", msg.getInReplyTo());
        }
        if (mode != MailEnums.ReplyMode.FORWARD && msg.getReferencesHeader() != null) {
            headers.put("References", msg.getReferencesHeader());
        }
        return headers;
    }

    private record Recipients(List<String> to, List<String> cc) {
    }
}
