package com.prabhix.platform.common.spi;

/**
 * Mail's outbox depth, declared here so the ops hub can show it without compiling against
 * {@code mail}. The mail module supplies the implementation.
 *
 * <p>Cross-tenant on purpose: this feeds an operator's view of the whole platform, not a tenant's
 * view of itself, and is the one place where a count that ignores the tenant filter is correct.
 */
public interface MailQueueMetrics {

    OutboxDepth outboxDepth();

    /**
     * @param inFlight not yet handed to a transport, including rows a worker has claimed
     * @param failed needs a human — attempts exhausted, or failing and still retrying
     */
    record OutboxDepth(long inFlight, long failed) {

        public static final OutboxDepth EMPTY = new OutboxDepth(0, 0);
    }
}
