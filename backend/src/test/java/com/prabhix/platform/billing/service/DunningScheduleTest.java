package com.prabhix.platform.billing.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DunningScheduleTest {

    @Test
    void backoffUsesWideningDayGaps() {
        assertEquals(1, DunningSchedule.delayDaysAfterAttempt(0));
        assertEquals(3, DunningSchedule.delayDaysAfterAttempt(1));
        assertEquals(7, DunningSchedule.delayDaysAfterAttempt(2));
        assertEquals(7, DunningSchedule.delayDaysAfterAttempt(5));
    }

    @Test
    void nextRetryIsInTheFuture() {
        Instant next = DunningSchedule.nextRetryAt(0);
        assertTrue(next.isAfter(Instant.now()));
        assertTrue(next.isBefore(Instant.now().plus(2, ChronoUnit.DAYS)));
    }
}
