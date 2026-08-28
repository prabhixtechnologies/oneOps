package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.event.MailRequested;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadEvent;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailThreadEventRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SlaSweepScheduler {

    private static final int BATCH_SIZE = 100;

    private final MailThreadRepository threadRepository;
    private final MailThreadEventRepository eventRepository;
    private final MailboxRepository mailboxRepository;
    private final ApplicationEventPublisher events;
    private final PrabhixProperties properties;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sweepBreaches() {
        Instant now = Instant.now();
        List<MailThread> breaches;
        do {
            breaches = threadRepository.findSlaBreaches(now, PageRequest.of(0, BATCH_SIZE));
            for (MailThread thread : breaches) {
                processBreach(thread);
            }
        } while (breaches.size() == BATCH_SIZE);
    }

    private void processBreach(MailThread thread) {
        thread.setSlaBreachedAt(Instant.now());
        threadRepository.save(thread);

        MailThreadEvent event = new MailThreadEvent();
        event.setOrganizationId(thread.getOrganizationId());
        event.setThreadId(thread.getId());
        event.setEventType(MailEnums.ThreadEventType.SLA_BREACHED);
        eventRepository.save(event);

        mailboxRepository.findById(thread.getMailboxId()).ifPresent(mb -> {
            Map<String, Object> vars = new HashMap<>();
            vars.put("threadSubject", thread.getSubject());
            vars.put("mailboxName", mb.getName());
            vars.put("targetMinutes", String.valueOf(thread.getSlaPolicyFirstMins()));
            vars.put("customerEmail", thread.getCustomerEmail());
            vars.put("assigneeName", "Unassigned");
            vars.put("waitingFor", "overdue");
            vars.put("threadUrl", properties.urls().console() + "/inbox/threads/" + thread.getId());
            events.publishEvent(new MailRequested(
                    thread.getOrganizationId(), "mail.sla-breach", "en",
                    List.of(mb.getAddress()), vars, "sla-" + thread.getId(), 50));
        });
    }
}
