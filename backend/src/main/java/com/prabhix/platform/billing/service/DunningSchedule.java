package com.prabhix.platform.billing.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Widening backoff between dunning retries so we do not hammer the gateway every cron tick.
 */
public final class DunningSchedule {

    public static final int MAX_ATTEMPTS = 3;

    private DunningSchedule() {
    }

    public static long delayDaysAfterAttempt(int attempt) {
        return switch (attempt) {
            case 0 -> 1;
            case 1 -> 3;
            default -> 7;
        };
    }

    public static Instant nextRetryAt(int attempt) {
        return Instant.now().plus(delayDaysAfterAttempt(attempt), ChronoUnit.DAYS);
    }

    public static Instant initialGraceEnd() {
        return nextRetryAt(0);
    }
}
