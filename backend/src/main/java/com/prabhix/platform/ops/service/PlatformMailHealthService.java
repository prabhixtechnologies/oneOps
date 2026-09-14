package com.prabhix.platform.ops.service;

import com.prabhix.platform.common.spi.MailQueueMetrics;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.outbound.transport.SesTransport;
import com.prabhix.platform.mail.repository.MailDomainRepository;
import com.prabhix.platform.mail.repository.MailSuppressionRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.ops.dto.OpsDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.GetSendQuotaResponse;

import java.time.Instant;

/**
 * SES quota, outbox depth, domain verification and mailbox counts for the admin console.
 *
 * <p>SES numbers soft-fail: a send-only IAM role is a legitimate configuration, and reporting the
 * rest of mail health as down because GetSendQuota was denied would hide a real outbox problem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformMailHealthService {

    private final MailQueueMetrics mailQueue;
    private final MailboxRepository mailboxes;
    private final MailDomainRepository domains;
    private final MailSuppressionRepository suppressions;
    private final SesTransport sesTransport;
    private final com.prabhix.platform.config.PrabhixProperties properties;

    public OpsDtos.MailHealthResponse health() {
        MailQueueMetrics.OutboxDepth depth = mailQueue.outboxDepth();
        long verified = domains.countByStatusAndDeletedAtIsNull(MailEnums.DomainStatus.VERIFIED);
        long pending = domains.countByStatusAndDeletedAtIsNull(MailEnums.DomainStatus.PENDING)
                + domains.countByStatusAndDeletedAtIsNull(MailEnums.DomainStatus.VERIFYING);
        long totalDomains = domains.countByDeletedAtIsNull();
        long bounces = suppressions.countByReason(MailEnums.SuppressionReason.HARD_BOUNCE)
                + suppressions.countByReason(MailEnums.SuppressionReason.SOFT_BOUNCE);
        long complaints = suppressions.countByReason(MailEnums.SuppressionReason.COMPLAINT);

        return new OpsDtos.MailHealthResponse(
                ses(),
                new OpsDtos.MailHealthOutbox(depth.inFlight(), depth.failed()),
                new OpsDtos.MailHealthDomains(totalDomains, verified, pending),
                mailboxes.countByDeletedAtIsNull(),
                new OpsDtos.MailHealthSuppressions(bounces, complaints),
                Instant.now());
    }

    private OpsDtos.MailHealthSes ses() {
        boolean ok = sesTransport.healthy();
        String note = sesTransport.healthNote();
        Double max24 = null;
        Double rate = null;
        Double sent = null;
        Boolean production = note != null && note.toLowerCase().contains("sandbox") ? Boolean.FALSE : null;
        try (SesClient client = SesClient.builder()
                .region(Region.of(properties.mail().ses().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            GetSendQuotaResponse quota = client.getSendQuota();
            max24 = quota.max24HourSend();
            rate = quota.maxSendRate();
            sent = quota.sentLast24Hours();
        } catch (SdkException ex) {
            log.info("SES GetSendQuota unavailable: {}", ex.getMessage());
            if (note == null || note.isBlank()) {
                note = "SES quota unconfirmed (" + ex.getClass().getSimpleName() + ")";
            }
        }
        return new OpsDtos.MailHealthSes(ok, note, max24, rate, sent, production);
    }
}
