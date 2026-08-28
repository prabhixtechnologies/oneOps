package com.prabhix.platform.mail.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.inbound.MimeParser;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.mail.util.MailJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;

/** Computes SLA due times in business minutes, with pause/resume for PENDING_CUSTOMER. */
@Service
@RequiredArgsConstructor
public class SlaService {

    private final MailboxRepository mailboxRepository;
    private final MailThreadRepository threadRepository;

    public void onInboundMessage(MailThread thread, MimeParser.ParsedMime parsed) {
        if (thread.getStatus() == MailEnums.ThreadStatus.PENDING_CUSTOMER) {
            resumeSla(thread);
            thread.setStatus(MailEnums.ThreadStatus.OPEN);
        }
        if (thread.getSlaDueAt() == null) {
            computeSlaDue(thread);
        }
        threadRepository.save(thread);
    }

    public void onOutboundReply(MailThread thread) {
        if (thread.getFirstResponseAt() == null) {
            thread.setFirstResponseAt(Instant.now());
        }
        threadRepository.save(thread);
    }

    public void pauseIfPendingCustomer(MailThread thread) {
        if (thread.getStatus() == MailEnums.ThreadStatus.PENDING_CUSTOMER && thread.getSlaPausedAt() == null) {
            thread.setSlaPausedAt(Instant.now());
            threadRepository.save(thread);
        }
    }

    public void resumeSla(MailThread thread) {
        if (thread.getSlaPausedAt() != null) {
            long paused = Instant.now().toEpochMilli() - thread.getSlaPausedAt().toEpochMilli();
            thread.setSlaPausedMs(thread.getSlaPausedMs() + paused);
            thread.setSlaPausedAt(null);
            if (thread.getSlaDueAt() != null) {
                thread.setSlaDueAt(thread.getSlaDueAt().plusMillis(paused));
            }
        }
    }

    public void computeSlaDue(MailThread thread) {
        mailboxRepository.findById(thread.getMailboxId()).ifPresent(mailbox -> {
            Integer mins = thread.getSlaPolicyFirstMins() != null
                    ? thread.getSlaPolicyFirstMins()
                    : mailbox.getSlaFirstResponseMins();
            if (mins == null || mins <= 0) {
                return;
            }
            Instant due = addBusinessMinutes(Instant.now(), mins, mailbox);
            thread.setSlaDueAt(due);
            thread.setSlaPolicyFirstMins(mins);
        });
    }

    public Instant addBusinessMinutes(Instant start, int businessMinutes, Mailbox mailbox) {
        ZoneId zone = ZoneId.of(mailbox.getTimezone());
        ZonedDateTime cursor = start.atZone(zone);
        int remaining = businessMinutes;
        JsonNode hours = MailJson.mapper().valueToTree(MailJson.parseMap(mailbox.getBusinessHours()));

        while (remaining > 0) {
            if (isHoliday(cursor.toLocalDate(), hours)) {
                cursor = cursor.plusDays(1).with(LocalTime.MIN);
                continue;
            }
            DayOfWeek dow = cursor.getDayOfWeek();
            JsonNode dayConfig = hours.get(dow.name().toLowerCase());
            if (dayConfig == null || !dayConfig.has("start") || !dayConfig.has("end")) {
                cursor = cursor.plusDays(1).with(LocalTime.MIN);
                continue;
            }
            LocalTime startTime = LocalTime.parse(dayConfig.get("start").asText());
            LocalTime endTime = LocalTime.parse(dayConfig.get("end").asText());
            ZonedDateTime windowStart = cursor.toLocalDate().atTime(startTime).atZone(zone);
            ZonedDateTime windowEnd = cursor.toLocalDate().atTime(endTime).atZone(zone);

            if (cursor.isBefore(windowStart)) {
                cursor = windowStart;
            }
            if (cursor.isAfter(windowEnd) || cursor.isEqual(windowEnd)) {
                cursor = cursor.plusDays(1).with(LocalTime.MIN);
                continue;
            }
            long available = ChronoUnit.MINUTES.between(cursor, windowEnd);
            if (available >= remaining) {
                return cursor.plusMinutes(remaining).toInstant();
            }
            remaining -= (int) available;
            cursor = cursor.plusDays(1).with(LocalTime.MIN);
        }
        return cursor.toInstant();
    }

    private boolean isHoliday(LocalDate date, JsonNode hours) {
        JsonNode holidays = hours.get("holidays");
        if (holidays == null || !holidays.isArray()) {
            return false;
        }
        Iterator<JsonNode> it = holidays.elements();
        while (it.hasNext()) {
            if (date.toString().equals(it.next().asText())) {
                return true;
            }
        }
        return false;
    }
}
