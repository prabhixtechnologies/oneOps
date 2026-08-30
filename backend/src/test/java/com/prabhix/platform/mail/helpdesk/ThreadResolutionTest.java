package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadEvent;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.mailbox.MailboxAccess;
import com.prabhix.platform.mail.repository.*;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Closing a thread has to record who closed it and take it off the mailbox's open count, and
 * reopening has to undo both. The counter only ever grew before, so the badge on a mailbox that had
 * handled everything still showed its lifetime total.
 */
@ExtendWith(MockitoExtension.class)
class ThreadResolutionTest {

    @Mock private MailThreadRepository threadRepository;
    @Mock private MailMessageRepository messageRepository;
    @Mock private MailThreadNoteRepository noteRepository;
    @Mock private MailThreadEventRepository eventRepository;
    @Mock private MailThreadTagRepository threadTagRepository;
    @Mock private MailboxRepository mailboxRepository;
    @Mock private MailboxAccess mailboxAccess;
    @Mock private SlaService slaService;

    private ThreadService threadService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private final UUID mailboxId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        threadService = new ThreadService(
                threadRepository, messageRepository, noteRepository, eventRepository,
                threadTagRepository, mailboxRepository, mailboxAccess, slaService, properties);
    }

    @Test
    void resolvingStampsResolutionAndDecrementsOpenCount() {
        MailThread thread = stubThread(MailEnums.ThreadStatus.OPEN);
        Mailbox mailbox = stubMailbox(4);

        threadService.update(principal(), threadId,
                new ThreadDtos.UpdateThreadRequest(MailEnums.ThreadStatus.RESOLVED, null));

        assertNotNull(thread.getResolvedAt());
        assertEquals(actorId, thread.getResolvedBy());
        assertEquals(3, mailbox.getOpenThreadCount());
        assertEquals(MailEnums.ThreadEventType.STATUS_CHANGED, capturedEvent().getEventType());
    }

    @Test
    void reopeningClearsResolutionAndIncrementsOpenCount() {
        MailThread thread = stubThread(MailEnums.ThreadStatus.RESOLVED);
        thread.setResolvedAt(java.time.Instant.now());
        thread.setResolvedBy(UUID.randomUUID());
        Mailbox mailbox = stubMailbox(2);

        threadService.update(principal(), threadId,
                new ThreadDtos.UpdateThreadRequest(MailEnums.ThreadStatus.OPEN, null));

        assertNull(thread.getResolvedAt());
        assertNull(thread.getResolvedBy());
        assertEquals(3, mailbox.getOpenThreadCount());
    }

    /**
     * Spam is not open work either, so it decrements. Moving from resolved to closed does not, because
     * both are already off the queue and double-counting the same transition is how the drift started.
     */
    @Test
    void movingBetweenTwoClosedStatusesLeavesTheCountAlone() {
        stubThread(MailEnums.ThreadStatus.RESOLVED);

        threadService.update(principal(), threadId,
                new ThreadDtos.UpdateThreadRequest(MailEnums.ThreadStatus.CLOSED, null));

        verify(mailboxRepository, never()).findById(any());
        verify(mailboxRepository, never()).save(any());
    }

    @Test
    void openCountIsFlooredAtZero() {
        stubThread(MailEnums.ThreadStatus.OPEN);
        Mailbox mailbox = stubMailbox(0);

        threadService.update(principal(), threadId,
                new ThreadDtos.UpdateThreadRequest(MailEnums.ThreadStatus.CLOSED, null));

        assertEquals(0, mailbox.getOpenThreadCount());
    }

    @Test
    void priorityChangeEmitsItsOwnEvent() {
        MailThread thread = stubThread(MailEnums.ThreadStatus.OPEN);
        thread.setPriority(MailEnums.Priority.NORMAL);

        threadService.update(principal(), threadId,
                new ThreadDtos.UpdateThreadRequest(null, MailEnums.Priority.URGENT));

        MailThreadEvent event = capturedEvent();
        assertEquals(MailEnums.ThreadEventType.PRIORITY_CHANGED, event.getEventType());
        assertEquals("NORMAL", event.getFromValue());
        assertEquals("URGENT", event.getToValue());
    }

    private MailThread stubThread(MailEnums.ThreadStatus status) {
        MailThread thread = new MailThread();
        thread.setId(threadId);
        thread.setOrganizationId(orgId);
        thread.setMailboxId(mailboxId);
        thread.setStatus(status);
        when(threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(threadId, orgId))
                .thenReturn(Optional.of(thread));
        when(threadRepository.save(thread)).thenReturn(thread);
        return thread;
    }

    private Mailbox stubMailbox(int openCount) {
        Mailbox mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setOrganizationId(orgId);
        mailbox.setOpenThreadCount(openCount);
        when(mailboxRepository.findById(mailboxId)).thenReturn(Optional.of(mailbox));
        return mailbox;
    }

    private MailThreadEvent capturedEvent() {
        ArgumentCaptor<MailThreadEvent> captor = ArgumentCaptor.forClass(MailThreadEvent.class);
        verify(eventRepository).save(captor.capture());
        return captor.getValue();
    }

    private PrabhixPrincipal principal() {
        return new PrabhixPrincipal(actorId, "lead@acme.com", "Lead", orgId,
                Set.of(Permission.MAIL_READ_ALL), UUID.randomUUID(), false);
    }

    @Test
    void summaryCarriesTheThreadsTags() {
        stubThread(MailEnums.ThreadStatus.OPEN);
        UUID tagId = UUID.randomUUID();
        when(threadTagRepository.findTagsForThreads(orgId, List.of(threadId)))
                .thenReturn(List.of(tagRow(tagId, "billing", "Billing", "#ff0000")));

        ThreadDtos.ThreadSummary summary = threadService.update(principal(), threadId,
                new ThreadDtos.UpdateThreadRequest(null, null));

        assertEquals(1, summary.tags().size());
        assertEquals(tagId, summary.tags().getFirst().id());
        assertEquals("Billing", summary.tags().getFirst().name());
    }

    private MailThreadTagRepository.ThreadTagView tagRow(UUID tagId, String slug, String name, String colour) {
        return new MailThreadTagRepository.ThreadTagView() {
            @Override public UUID getThreadId() { return threadId; }
            @Override public UUID getTagId() { return tagId; }
            @Override public String getSlug() { return slug; }
            @Override public String getName() { return name; }
            @Override public String getColour() { return colour; }
        };
    }
}
