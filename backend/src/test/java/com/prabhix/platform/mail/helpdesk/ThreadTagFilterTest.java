package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.repository.*;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadTagFilterTest {

    @Mock private MailThreadRepository threadRepository;
    @Mock private MailMessageRepository messageRepository;
    @Mock private MailThreadNoteRepository noteRepository;
    @Mock private MailThreadEventRepository eventRepository;
    @Mock private MailboxMemberRepository memberRepository;
    @Mock private SlaService slaService;

    private ThreadService threadService;

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        threadService = new ThreadService(
                threadRepository, messageRepository, noteRepository, eventRepository,
                memberRepository, slaService, properties);
    }

    @Test
    void passesTagIdToRepositoryQuery() {
        UUID orgId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        PrabhixPrincipal principal = orgPrincipal(orgId);

        when(threadRepository.listWithCursor(
                eq(orgId), isNull(), isNull(), isNull(), isNull(), isNull(), eq(tagId),
                eq(false), eq(false), eq(true), any(), any(), any(), eq(26)))
                .thenReturn(List.of(sampleThread(orgId)));

        ThreadDtos.ThreadListQuery query = new ThreadDtos.ThreadListQuery(
                null, null, null, null, null, tagId, false, false, null, null, 25);

        CursorPage<ThreadDtos.ThreadSummary> page = threadService.list(principal, query);

        assertEquals(1, page.items().size());
        ArgumentCaptor<UUID> tagCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(threadRepository).listWithCursor(
                eq(orgId), isNull(), isNull(), isNull(), isNull(), isNull(), tagCaptor.capture(),
                eq(false), eq(false), eq(true), any(), any(), any(), eq(26));
        assertEquals(tagId, tagCaptor.getValue());
    }

    private PrabhixPrincipal orgPrincipal(UUID orgId) {
        return new PrabhixPrincipal(UUID.randomUUID(), "test@example.com", "Test User", orgId,
                Set.of(Permission.MAIL_READ_ALL), UUID.randomUUID(), false);
    }

    private MailThread sampleThread(UUID orgId) {
        MailThread thread = new MailThread();
        thread.setId(UUID.randomUUID());
        thread.setOrganizationId(orgId);
        thread.setMailboxId(UUID.randomUUID());
        thread.setReferenceKey("REF001");
        thread.setSubject("Help");
        thread.setLastMessageAt(Instant.now());
        return thread;
    }
}
