package com.prabhix.platform.common.mail;

import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.common.util.OutboxBackoff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Moves committed requests across the seam to whatever is handling mail.
 *
 * <p>The only thing that reads {@code mail_requests}. Today the handler is the mail module in this
 * process; when mail is its own service it becomes an HTTP call, and the callers, the client and
 * this class are unchanged.
 */
@Slf4j
@Component
public class MailRequestRelay {

    /** A claim older than this belonged to a process that is not coming back. */
    private static final Duration STALE_CLAIM = Duration.ofMinutes(10);
    private static final int BATCH_SIZE = 100;

    private final String instanceId = "relay-" + UUID.randomUUID().toString().substring(0, 8);

    private final MailRequestRepository requests;

    /**
     * Lazily resolved rather than injected.
     *
     * <p>The handler lives in the mail module and this class is in {@code common}, which mail
     * depends on. A constructor dependency the other way would be a cycle at startup, and would also
     * make the platform refuse to boot without a mail implementation present — which is exactly the
     * arrangement this seam exists to end.
     */
    private final ObjectProvider<MailRequestHandler> handler;

    public MailRequestRelay(MailRequestRepository requests, ObjectProvider<MailRequestHandler> handler) {
        this.requests = requests;
        this.handler = handler;
    }

    @Scheduled(fixedDelayString = "${prabhix.mail.requests.poll-interval:PT5S}")
    @Transactional
    public void drain() {
        MailRequestHandler target = handler.getIfAvailable();
        if (target == null) {
            // Nothing is handling mail in this deployment. The rows keep, which is the point of
            // writing them down, so this is a pause rather than a loss.
            return;
        }

        requests.releaseStuck(Instant.now().minus(STALE_CLAIM));

        Instant now = Instant.now();
        List<MailRequestRecord> batch = requests.claimPending(now, BATCH_SIZE);
        batch.addAll(requests.claimRetryable(now, BATCH_SIZE));

        for (MailRequestRecord row : batch) {
            row.setStatus(MailRequestRecord.Status.CLAIMED);
            row.setClaimedAt(now);
            row.setClaimedBy(instanceId);
            requests.save(row);
            deliver(target, row);
        }
    }

    private void deliver(MailRequestHandler target, MailRequestRecord row) {
        try {
            UUID accepted = target.accept(new MailRequest(
                    row.getOrganizationId(),
                    row.getTemplateKey(),
                    row.getLocale(),
                    Json.parseStringList(row.getToAddresses()),
                    Json.parseMap(row.getVariables()),
                    row.getDedupeKey(),
                    row.getPriority()));

            row.setStatus(MailRequestRecord.Status.DELIVERED);
            row.setDeliveredAt(Instant.now());
            row.setOutboxId(accepted);
            row.setLastError(null);
        } catch (RuntimeException ex) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(truncate(ex.getMessage()));

            if (row.getAttempts() >= row.getMaxAttempts()) {
                // DEAD, not deleted. A request that was never delivered is the record of an email
                // somebody is still waiting for, and the row is the only place that says so.
                row.setStatus(MailRequestRecord.Status.DEAD);
                log.error("Mail request {} ({}) gave up after {} attempts: {}",
                        row.getId(), row.getTemplateKey(), row.getAttempts(), row.getLastError());
            } else {
                row.setStatus(MailRequestRecord.Status.FAILED);
                row.setNextAttemptAt(Instant.now().plus(OutboxBackoff.nextDelay(row.getAttempts())));
                log.warn("Mail request {} ({}) failed on attempt {}, retrying: {}",
                        row.getId(), row.getTemplateKey(), row.getAttempts(), row.getLastError());
            }
        }
        requests.save(row);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "no message";
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
