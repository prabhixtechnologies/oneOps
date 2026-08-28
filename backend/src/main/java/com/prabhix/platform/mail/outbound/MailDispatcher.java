package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.common.event.MailRequested;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.mail.util.MailJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public API for enqueueing outbound mail. */
@Service
@RequiredArgsConstructor
public class MailDispatcher {

    private final MailOutboxRepository outboxRepository;
    private final PrabhixProperties properties;

    @Transactional
    public UUID enqueue(UUID organizationId, String templateKey, String locale,
                        List<String> to, Map<String, Object> variables,
                        String dedupeKey, int priority) {
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            UUID inserted = outboxRepository.insertWithDedupe(
                    organizationId, templateKey, locale != null ? locale : "en",
                    MailJson.toJson(variables), properties.mail().fromAddress(),
                    properties.mail().fromName(), properties.mail().replyTo(),
                    MailJson.toJson(to), dedupeKey, priority,
                    properties.mail().outbox().maxAttempts());
            if (inserted != null) {
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
        row.setTemplateVariables(MailJson.toJson(variables));
        row.setFromAddress(properties.mail().fromAddress());
        row.setFromName(properties.mail().fromName());
        row.setReplyTo(properties.mail().replyTo());
        row.setToAddresses(MailJson.toJson(to));
        row.setPriority(priority);
        row.setStatus(MailEnums.OutboxStatus.PENDING);
        row.setScheduledAt(Instant.now());
        row.setMaxAttempts(properties.mail().outbox().maxAttempts());
        return outboxRepository.save(row).getId();
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
                return inserted;
            }
            return outboxRepository.findByDedupeKey(row.getDedupeKey())
                    .map(MailOutbox::getId)
                    .orElseThrow(() -> new IllegalStateException("Dedupe conflict without existing row"));
        }
        return outboxRepository.save(row).getId();
    }

    // The publisher's transaction has already committed, so this needs a transaction of its
    // own. It also cannot rely on enqueue()'s @Transactional: that is a self-invocation and
    // would bypass the proxy.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMailRequested(MailRequested event) {
        enqueue(event.organizationId(), event.templateKey(), event.locale(),
                event.to(), event.variables(), event.dedupeKey(), event.priority());
    }
}
