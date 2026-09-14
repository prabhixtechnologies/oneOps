package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.common.mail.MailRequest;
import com.prabhix.platform.common.mail.MailRequestHandler;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.common.util.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enqueueing outbound mail, for the mail module's own use and for the relay.
 *
 * <p>{@code enqueue} and {@code enqueueDirect} are internal to mail — the helpdesk reply path and
 * compose use them, and they take rendered messages. {@link #accept} is the seam, and takes an
 * intent.
 */
@Service
@RequiredArgsConstructor
public class MailDispatcher implements MailRequestHandler {

    private final MailOutboxRepository outboxRepository;
    private final PrabhixProperties properties;
    private final StructuredEventLogger eventLogger;

    @Transactional
    public UUID enqueue(UUID organizationId, String templateKey, String locale,
                        List<String> to, Map<String, Object> variables,
                        String dedupeKey, int priority) {
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            UUID inserted = outboxRepository.insertWithDedupe(
                    organizationId, templateKey, locale != null ? locale : "en",
                    Json.toJson(variables), properties.mail().fromAddress(),
                    properties.mail().fromName(), properties.mail().replyTo(),
                    Json.toJson(to), dedupeKey, priority,
                    properties.mail().outbox().maxAttempts());
            if (inserted != null) {
                eventLogger.log(LogEventCode.MAIL_OUTBOUND_QUEUED, Map.of("outboxId", inserted));
                return inserted;
            }
            return outboxRepository.findByDedupeKey(dedupeKey)
                    .map(MailOutbox::getId)
                    .orElseThrow(() -> new IllegalStateException("Dedupe conflict without existing row"));
        }

        MailOutbox row = new MailOutbox();
        row.setOrganizationId(organizationId);
        row.setTemplateKey(templateKey);
        row.setLocale(locale != null ? locale : "en");
        row.setTemplateVariables(Json.toJson(variables));
        row.setFromAddress(properties.mail().fromAddress());
        row.setFromName(properties.mail().fromName());
        row.setReplyTo(properties.mail().replyTo());
        row.setToAddresses(Json.toJson(to));
        row.setPriority(priority);
        row.setStatus(MailEnums.OutboxStatus.PENDING);
        row.setScheduledAt(Instant.now());
        row.setMaxAttempts(properties.mail().outbox().maxAttempts());
        UUID id = outboxRepository.save(row).getId();
        eventLogger.log(LogEventCode.MAIL_OUTBOUND_QUEUED, Map.of("outboxId", id));
        return id;
    }

    @Transactional
    public UUID enqueueDirect(MailOutbox row) {
        row.setStatus(MailEnums.OutboxStatus.PENDING);
        row.setScheduledAt(Instant.now());
        if (row.getMaxAttempts() <= 0) {
            row.setMaxAttempts(properties.mail().outbox().maxAttempts());
        }

        if (row.getDedupeKey() != null && !row.getDedupeKey().isBlank()) {
            UUID inserted = outboxRepository.insertDirectWithDedupe(
                    row.getOrganizationId(), row.getMailboxId(), row.getThreadId(),
                    row.getMessageId(), row.getFromAddress(), row.getFromName(), row.getReplyTo(),
                    row.getToAddresses(), row.getCcAddresses(), row.getBccAddresses(),
                    row.getSubject(), row.getBodyHtml(), row.getBodyText(), row.getHeaders(),
                    row.getAttachmentIds(), row.getDedupeKey(), row.getPriority(),
                    row.getMaxAttempts());
            if (inserted != null) {
                eventLogger.log(LogEventCode.MAIL_OUTBOUND_QUEUED, Map.of("outboxId", inserted));
                return inserted;
            }
            return outboxRepository.findByDedupeKey(row.getDedupeKey())
                    .map(MailOutbox::getId)
                    .orElseThrow(() -> new IllegalStateException("Dedupe conflict without existing row"));
        }
        UUID id = outboxRepository.save(row).getId();
        eventLogger.log(LogEventCode.MAIL_OUTBOUND_QUEUED, Map.of("outboxId", id));
        return id;
    }

    /**
     * The far side of the seam. Called by the relay, never by a module wanting mail.
     *
     * <p>Replaces an {@code @TransactionalEventListener(AFTER_COMMIT)} on the same class. That ran
     * after the requesting transaction had committed and outside any transaction of its own, so a
     * process that died in between lost the request with no record it had been made. The relay's row
     * is committed before this is reached, so a failure here is a retry rather than a loss.
     *
     * <p>Idempotent through {@code dedupeKey}, which the relay depends on: a delivery whose
     * acknowledgement was lost is retried, and must not become a second email.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID accept(MailRequest request) {
        return enqueue(request.organizationId(), request.templateKey(), request.locale(),
                request.to(), request.variables(), request.dedupeKey(), request.priority());
    }
}
