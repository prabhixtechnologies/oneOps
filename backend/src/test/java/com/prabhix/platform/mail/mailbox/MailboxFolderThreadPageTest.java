package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailFolder;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailFolderRepository;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailboxFolderThreadPageTest {

    @Mock private MailThreadRepository threadRepository;
    @Mock private MailMessageRepository messageRepository;
    @Mock private MailFolderRepository folderRepository;
    @Mock private MailFolderService folders;
    @Mock private MailFlagService flags;
    @Mock private MailboxAccess access;
    @Mock private OrganizationMembershipRepository membershipRepository;

    private MailboxListService listService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID folderId = UUID.randomUUID();
    private final UUID mailboxId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        listService = new MailboxListService(
                threadRepository, messageRepository, folderRepository, folders, flags,
                access, membershipRepository, properties);
    }

    @Test
    void requiresMailboxAccessBeforeListing() {
        MailFolder folder = new MailFolder();
        folder.setId(folderId);
        folder.setMailboxId(mailboxId);
        folder.setOrganizationId(orgId);
        when(folderRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(folderId, orgId))
                .thenReturn(Optional.of(folder));
        when(access.requireMailbox(any(), eq(mailboxId)))
                .thenThrow(ApiException.forbidden("denied"));

        assertThrows(ApiException.class, () -> listService.threadsInPage(principal(false), query(null)));
    }

    @Test
    void searchUsesMessageFullTextPath() {
        stubFolderAccess();
        MailThread thread = sampleThread();
        when(threadRepository.searchInFolderWithCursor(
                eq(orgId), eq(folderId), eq(userId),
                eq(false), eq(true), isNull(), isNull(), eq("invoice"),
                any(), any(), eq(26)))
                .thenReturn(List.of(thread));
        when(flags.flagsFor(eq(userId), anyList())).thenReturn(java.util.Map.of());
        when(flags.view(eq(thread), eq(folderId), isNull()))
                .thenReturn(new MailboxDtos.MailThreadView(
                        thread.getId(), mailboxId, folderId, "Inv", "snip", "a@b.c", null,
                        1, true, false, false, null, thread.getLastMessageAt(), null));

        MailboxDtos.FolderThreadListQuery q = new MailboxDtos.FolderThreadListQuery(
                folderId, "invoice", false, true, null, null, null, 25);
        CursorPage<MailboxDtos.MailThreadView> page = listService.threadsInPage(principal(false), q);

        assertEquals(1, page.items().size());
        verify(threadRepository).searchInFolderWithCursor(
                eq(orgId), eq(folderId), eq(userId),
                eq(false), eq(true), isNull(), isNull(), eq("invoice"),
                any(), any(), eq(26));
    }

    @Test
    void filterListingUsesKeysetQueryWithoutSearch() {
        stubFolderAccess();
        MailThread t1 = sampleThread();
        MailThread t2 = sampleThread();
        t2.setLastMessageAt(Instant.now().minusSeconds(60));
        when(threadRepository.listInFolderWithCursor(
                eq(orgId), eq(folderId), eq(userId),
                eq(true), eq(false), any(), any(),
                any(), any(), eq(3)))
                .thenReturn(List.of(t1, t2, sampleThread()));
        when(flags.flagsFor(eq(userId), anyList())).thenReturn(java.util.Map.of());
        when(flags.view(any(), eq(folderId), isNull()))
                .thenAnswer(inv -> {
                    MailThread t = inv.getArgument(0);
                    return new MailboxDtos.MailThreadView(
                            t.getId(), mailboxId, folderId, "S", "sn", "a@b.c", null,
                            1, false, false, false, null, t.getLastMessageAt(), null);
                });

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T23:59:59Z");
        MailboxDtos.FolderThreadListQuery q = new MailboxDtos.FolderThreadListQuery(
                folderId, null, true, false, from, to, null, 2);
        CursorPage<MailboxDtos.MailThreadView> page = listService.threadsInPage(principal(false), q);

        assertEquals(2, page.items().size());
        assertEquals(true, page.hasMore());
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(threadRepository).listInFolderWithCursor(
                eq(orgId), eq(folderId), eq(userId),
                eq(true), eq(false), fromCaptor.capture(), eq(to),
                any(), any(), eq(3));
        assertEquals(from, fromCaptor.getValue());
    }

    private void stubFolderAccess() {
        MailFolder folder = new MailFolder();
        folder.setId(folderId);
        folder.setMailboxId(mailboxId);
        folder.setOrganizationId(orgId);
        when(folderRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(folderId, orgId))
                .thenReturn(Optional.of(folder));
        when(access.requireMailbox(any(), eq(mailboxId))).thenReturn(new Mailbox());
    }

    private MailboxDtos.FolderThreadListQuery query(String q) {
        return new MailboxDtos.FolderThreadListQuery(folderId, q, false, false, null, null, null, 25);
    }

    private PrabhixPrincipal principal(boolean readAll) {
        Set<Permission> perms = readAll
                ? Set.of(Permission.MAIL_READ, Permission.MAIL_READ_ALL)
                : Set.of(Permission.MAIL_READ);
        return new PrabhixPrincipal(userId, "u@example.com", "User", orgId, perms, UUID.randomUUID(), false);
    }

    private MailThread sampleThread() {
        MailThread thread = new MailThread();
        thread.setId(UUID.randomUUID());
        thread.setOrganizationId(orgId);
        thread.setMailboxId(mailboxId);
        thread.setSubject("Subject");
        thread.setLastMessageAt(Instant.now());
        return thread;
    }
}
