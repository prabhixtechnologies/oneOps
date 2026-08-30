package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.common.spi.MailQueueMetrics;
import com.prabhix.platform.mail.domain.MailEnums.OutboxStatus;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Mail's implementation of {@link MailQueueMetrics}.
 *
 * <p>Which statuses mean "stuck" is mail's business, and these two sets used to sit in the ops
 * service — so a new outbox status would have been added here and silently missed by the dashboard
 * that exists to notice it.
 */
@Service
@RequiredArgsConstructor
public class MailQueueMetricsService implements MailQueueMetrics {

    /** Anything not yet handed to a transport, including rows a worker has claimed mid-flight. */
    private static final Set<OutboxStatus> IN_FLIGHT =
            EnumSet.of(OutboxStatus.PENDING, OutboxStatus.CLAIMED, OutboxStatus.SENDING);

    /** DEAD counts as failed: attempts are exhausted, so it needs a human either way. */
    private static final Set<OutboxStatus> FAILED =
            EnumSet.of(OutboxStatus.FAILED, OutboxStatus.DEAD);

    private final MailOutboxRepository outboxRepository;

    @Override
    @Transactional(readOnly = true)
    public OutboxDepth outboxDepth() {
        return new OutboxDepth(
                outboxRepository.countByStatusIn(IN_FLIGHT),
                outboxRepository.countByStatusIn(FAILED));
    }
}
