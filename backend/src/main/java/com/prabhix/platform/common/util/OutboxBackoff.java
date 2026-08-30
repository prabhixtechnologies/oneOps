package com.prabhix.platform.common.util;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter for outbox retries.
 *
 * <p>Lived in {@code mail.util} and was used by the push outbox from there, which made push
 * depend on mail for arithmetic. Two outboxes want the same retry curve and neither owns it.
 */
public final class OutboxBackoff {

    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration MAX = Duration.ofHours(6);

    private OutboxBackoff() {
    }

    public static Duration nextDelay(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        long baseMs = BASE.toMillis();
        long exponential = baseMs * (1L << Math.min(attempt - 1, 10));
        long capped = Math.min(exponential, MAX.toMillis());
        long jitter = ThreadLocalRandom.current().nextLong(capped / 4 + 1);
        return Duration.ofMillis(capped + jitter);
    }
}
