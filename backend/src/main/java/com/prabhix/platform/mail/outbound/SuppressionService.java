package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.mail.domain.MailDeliveryEvent;
import com.prabhix.platform.mail.domain.MailSuppression;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailDeliveryEventRepository;
import com.prabhix.platform.mail.repository.MailSuppressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SuppressionService {

    private static final Pattern DSN_RECIPIENT = Pattern.compile(
            "Final-Recipient:\\s*rfc822;\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_CODE = Pattern.compile("(\\d\\.\\d\\.\\d)");

    private final MailSuppressionRepository suppressionRepository;
    private final MailDeliveryEventRepository deliveryEventRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public boolean isSuppressed(String address, UUID organizationId) {
        return suppressionRepository.findActive(address, organizationId, Instant.now()).isPresent();
    }

    @Transactional
    public void recordHardBounce(String address, UUID organizationId, String detail) {
        upsert(address, organizationId, MailEnums.SuppressionReason.HARD_BOUNCE, detail, null);
    }

    @Transactional
    public void recordSoftBounce(String address, UUID organizationId, String detail) {
        Optional<MailSuppression> existing = suppressionRepository.findActive(address, organizationId, Instant.now());
        if (existing.isPresent() && existing.get().getReason() == MailEnums.SuppressionReason.SOFT_BOUNCE) {
            MailSuppression s = existing.get();
            s.setBounceCount(s.getBounceCount() + 1);
            s.setLastBounceAt(Instant.now());
            if (s.getBounceCount() >= 3) {
                s.setReason(MailEnums.SuppressionReason.HARD_BOUNCE);
                s.setExpiresAt(null);
            }
            suppressionRepository.save(s);
        } else {
            upsert(address, organizationId, MailEnums.SuppressionReason.SOFT_BOUNCE, detail,
                    Instant.now().plus(7, ChronoUnit.DAYS));
        }
    }

    @Transactional
    public void recordComplaint(String address, UUID organizationId, String detail) {
        upsert(address, organizationId, MailEnums.SuppressionReason.COMPLAINT, detail, null);
        events.publishEvent(AuditRequested.of(organizationId, null,
                "mail.suppression.complaint", "mail_suppression", null));
    }

    @Transactional
    public void recordUnsubscribe(String address, UUID organizationId) {
        upsert(address, organizationId, MailEnums.SuppressionReason.UNSUBSCRIBE, null, null);
    }

    /** Parses a DSN bounce report and records the appropriate suppression or delivery event. */
    @Transactional
    public void parseAndRecordDsn(String dsnBody, UUID organizationId) {
        Matcher recipient = DSN_RECIPIENT.matcher(dsnBody);
        if (!recipient.find()) {
            return;
        }
        String address = recipient.group(1);
        Matcher status = STATUS_CODE.matcher(dsnBody);
        String code = status.find() ? status.group(1) : "";
        if (code.startsWith("5.")) {
            recordHardBounce(address, organizationId, code);
        } else if (code.startsWith("4.")) {
            recordSoftBounceDeliveryEvent(address, organizationId, code);
            recordSoftBounce(address, organizationId, code);
        }
    }

    private void recordSoftBounceDeliveryEvent(String address, UUID organizationId, String detail) {
        MailDeliveryEvent event = new MailDeliveryEvent();
        event.setOrganizationId(organizationId);
        event.setAddress(address);
        event.setEventType(MailEnums.DeliveryEventType.DEFERRED);
        event.setProviderCode(detail);
        event.setDetail(detail);
        event.setOccurredAt(Instant.now());
        deliveryEventRepository.save(event);
    }

    private void upsert(String address, UUID organizationId, MailEnums.SuppressionReason reason,
                        String detail, Instant expiresAt) {
        MailSuppression s = suppressionRepository.findActive(address, organizationId, Instant.now())
                .orElseGet(MailSuppression::new);
        s.setAddress(address);
        s.setOrganizationId(organizationId);
        s.setReason(reason);
        s.setDetail(detail);
        s.setExpiresAt(expiresAt);
        s.setLastBounceAt(Instant.now());
        suppressionRepository.save(s);
    }
}
