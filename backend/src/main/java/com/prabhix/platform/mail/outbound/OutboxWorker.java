package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailDeliveryEvent;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailDeliveryEventRepository;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.mail.outbound.transport.MailTransport;
import com.prabhix.platform.mail.outbound.transport.MailTransportRouter;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.common.util.OutboxBackoff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private static final Duration STALE_CLAIM = Duration.ofMinutes(10);
    private final String instanceId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    private final PrabhixProperties properties;
    private final MailOutboxRepository outboxRepository;
    private final MailDeliveryEventRepository deliveryEventRepository;
    private final TemplateRenderer templateRenderer;
    private final SuppressionService suppressionService;
    private final MailTransportRouter transportRouter;
    private final MailTrackingInjector trackingInjector;
    private final OutboundAttachmentResolver attachmentResolver;

    @Scheduled(fixedDelayString = "${prabhix.mail.outbox.poll-interval}")
    @Transactional
    public void drain() {
        if (!properties.mail().outbox().enabled()) {
            return;
        }
        outboxRepository.releaseStuck(Instant.now().minus(STALE_CLAIM));

        int batchSize = properties.mail().outbox().batchSize();
        Instant now = Instant.now();
        var pending = outboxRepository.claimPending(now, batchSize);
        var retryable = outboxRepository.claimRetryable(now, batchSize);
        pending.addAll(retryable);

        for (MailOutbox row : pending) {
            row.setStatus(MailEnums.OutboxStatus.CLAIMED);
            row.setClaimedAt(now);
            row.setClaimedBy(instanceId);
            outboxRepository.save(row);
            processRow(row);
        }
    }

    void processRow(MailOutbox row) {
        List<String> recipients = Json.parseStringList(row.getToAddresses());
        for (String address : recipients) {
            if (suppressionService.isSuppressed(address, row.getOrganizationId())) {
                row.setStatus(MailEnums.OutboxStatus.SUPPRESSED);
                row.setLastError("Address suppressed: " + address);
                outboxRepository.save(row);
                return;
            }
        }

        try {
            row.setStatus(MailEnums.OutboxStatus.SENDING);
            outboxRepository.save(row);

            if (row.getTemplateKey() != null) {
                var rendered = templateRenderer.render(
                        row.getTemplateKey(), row.getLocale(), row.getOrganizationId(),
                        Json.parseMap(row.getTemplateVariables()));
                row.setSubject(rendered.subject());
                row.setBodyHtml(trackingInjector.inject(
                        rendered.bodyHtml(), row.getId(), rendered.trackingEnabled()));
                row.setBodyText(rendered.bodyText());
            } else if (row.getBodyHtml() != null) {
                row.setBodyHtml(trackingInjector.inject(row.getBodyHtml(), row.getId(), true));
            }

            MailTransport transport = transportRouter.select();
            MailTransport.OutboundMail mail = buildMail(row);
            var result = transport.send(mail);

            if (result.success()) {
                row.setStatus(MailEnums.OutboxStatus.SENT);
                row.setSentAt(Instant.now());
                row.setTransportUsed(transport.providerId());
                row.setProviderMessageId(result.providerMessageId());
                transportRouter.recordSuccess(transport.providerId());
                recordDelivery(row, MailEnums.DeliveryEventType.SENT, null);
            } else {
                handleFailure(row, transport.providerId(), result.error());
            }
        } catch (Exception ex) {
            handleFailure(row, "UNKNOWN", ex.getMessage());
        }
        outboxRepository.save(row);
    }

    void handleFailure(MailOutbox row, String providerId, String error) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastError(error);
        transportRouter.recordFailure(providerId);
        recordDelivery(row, MailEnums.DeliveryEventType.FAILED, error);

        if (row.getAttempts() >= row.getMaxAttempts()) {
            row.setStatus(MailEnums.OutboxStatus.DEAD);
        } else {
            row.setStatus(MailEnums.OutboxStatus.FAILED);
            var delay = OutboxBackoff.nextDelay(row.getAttempts());
            row.setNextAttemptAt(Instant.now().plus(delay));
        }
    }

    private MailTransport.OutboundMail buildMail(MailOutbox row) {
        MailTransport.OutboundMail mail = new MailTransport.OutboundMail();
        mail.setOrganizationId(row.getOrganizationId());
        mail.setFromAddress(row.getFromAddress());
        mail.setFromName(row.getFromName());
        mail.setReplyTo(row.getReplyTo());
        mail.setToAddresses(Json.parseStringList(row.getToAddresses()));
        mail.setCcAddresses(Json.parseStringList(row.getCcAddresses()));
        mail.setBccAddresses(Json.parseStringList(row.getBccAddresses()));
        mail.setSubject(row.getSubject());
        mail.setBodyHtml(row.getBodyHtml());
        mail.setBodyText(row.getBodyText());
        mail.setHeaders(Json.parseMap(row.getHeaders()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, e -> String.valueOf(e.getValue()))));
        if (row.getOrganizationId() != null) {
            mail.setAttachments(attachmentResolver.resolve(row.getOrganizationId(), row.getAttachmentIds()));
        }
        return mail;
    }

    private void recordDelivery(MailOutbox row, MailEnums.DeliveryEventType type, String detail) {
        for (String address : Json.parseStringList(row.getToAddresses())) {
            MailDeliveryEvent event = new MailDeliveryEvent();
            event.setOrganizationId(row.getOrganizationId());
            event.setOutboxId(row.getId());
            event.setMessageId(row.getMessageId());
            event.setAddress(address);
            event.setEventType(type);
            event.setDetail(detail);
            event.setOccurredAt(Instant.now());
            deliveryEventRepository.save(event);
        }
    }
}
