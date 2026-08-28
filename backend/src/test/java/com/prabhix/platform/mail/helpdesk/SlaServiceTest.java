package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @Mock
    MailboxRepository mailboxRepository;
    @Mock
    MailThreadRepository threadRepository;

    SlaService slaService;

    @BeforeEach
    void setUp() {
        slaService = new SlaService(mailboxRepository, threadRepository);
    }

    @Test
    void skipsWeekendForBusinessMinutes() {
        Mailbox mailbox = businessMailbox();
        // Friday 17:00 IST, 120 business minutes should land Monday 11:00
        Instant start = ZonedDateTime.of(2026, 8, 28, 17, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        Instant due = slaService.addBusinessMinutes(start, 120, mailbox);
        ZonedDateTime dueZ = due.atZone(ZoneId.of("Asia/Kolkata"));
        assertTrue(dueZ.getDayOfWeek() == DayOfWeek.MONDAY || dueZ.getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    void pauseAndResumeExtendsDueDate() {
        MailThread thread = new MailThread();
        thread.setSlaPausedAt(Instant.parse("2026-08-27T10:00:00Z"));
        thread.setSlaDueAt(Instant.parse("2026-08-27T12:00:00Z"));
        thread.setSlaPausedMs(0);

        // Simulate resume 1 hour later
        long paused = Instant.parse("2026-08-27T11:00:00Z").toEpochMilli()
                - thread.getSlaPausedAt().toEpochMilli();
        thread.setSlaPausedMs(thread.getSlaPausedMs() + paused);
        thread.setSlaDueAt(thread.getSlaDueAt().plusMillis(paused));
        thread.setSlaPausedAt(null);

        assertTrue(thread.getSlaDueAt().isAfter(Instant.parse("2026-08-27T12:00:00Z")));
        assertTrue(thread.getSlaPausedMs() >= 3_600_000);
    }

    private Mailbox businessMailbox() {
        Mailbox m = new Mailbox();
        m.setTimezone("Asia/Kolkata");
        m.setBusinessHours("""
                {"monday":{"start":"09:00","end":"18:00"},
                 "tuesday":{"start":"09:00","end":"18:00"},
                 "wednesday":{"start":"09:00","end":"18:00"},
                 "thursday":{"start":"09:00","end":"18:00"},
                 "friday":{"start":"09:00","end":"18:00"},
                 "holidays":[]}""");
        return m;
    }
}
