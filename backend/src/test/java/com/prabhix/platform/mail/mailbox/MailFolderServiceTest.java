package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailFolder;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadFolder;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailFolderRepository;
import com.prabhix.platform.mail.repository.MailThreadFolderRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailFolderServiceTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID mailboxId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private MailFolderRepository folderRepository;
    private MailThreadFolderRepository placementRepository;
    private MailboxAccess access;
    private MailFolderService service;

    private Mailbox mailbox;
    private PrabhixPrincipal principal;
    private List<MailFolder> stored;

    @BeforeEach
    void setUp() {
        folderRepository = mock(MailFolderRepository.class);
        placementRepository = mock(MailThreadFolderRepository.class);
        access = mock(MailboxAccess.class);
        service = new MailFolderService(folderRepository, placementRepository, access);

        mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setOrganizationId(orgId);
        mailbox.setAddress("support@prabhixtechnologies.com");
        mailbox.setName("Support");

        principal = mock(PrabhixPrincipal.class);
        when(principal.requireOrganizationId()).thenReturn(orgId);
        when(principal.userId()).thenReturn(userId);
        when(access.requireMailbox(any(), eq(mailboxId))).thenReturn(mailbox);

        stored = new ArrayList<>();
        when(folderRepository.save(any(MailFolder.class))).thenAnswer(inv -> {
            MailFolder f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            stored.removeIf(existing -> existing.getId().equals(f.getId()));
            stored.add(f);
            return f;
        });
        when(folderRepository.findByMailboxIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(mailboxId))
                .thenAnswer(inv -> List.copyOf(stored));
        when(folderRepository.countsByFolder(anyList(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("a mailbox with no folders gets the six system ones, once")
    void systemFoldersAreCreatedOnce() {
        when(folderRepository.existsByMailboxIdAndDeletedAtIsNull(mailboxId)).thenReturn(false);

        service.ensureSystemFolders(mailbox);

        assertThat(stored).extracting(MailFolder::getKind).containsExactlyInAnyOrder(
                MailEnums.FolderKind.INBOX, MailEnums.FolderKind.SENT, MailEnums.FolderKind.DRAFTS,
                MailEnums.FolderKind.ARCHIVE, MailEnums.FolderKind.SPAM, MailEnums.FolderKind.TRASH);

        when(folderRepository.existsByMailboxIdAndDeletedAtIsNull(mailboxId)).thenReturn(true);
        service.ensureSystemFolders(mailbox);

        assertThat(stored).hasSize(6);
    }

    @Test
    @DisplayName("two folders in the same place cannot share a name")
    void duplicateNameIsRejected() {
        seedSystemFolders();
        service.create(principal, mailboxId, new MailboxDtos.SaveFolderRequest("Invoices", null, null, null));

        assertThatThrownBy(() -> service.create(principal, mailboxId,
                new MailboxDtos.SaveFolderRequest("invoices", null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already here");
    }

    @Test
    @DisplayName("a folder cannot be moved inside its own sub-folder")
    void cyclesAreRejected() {
        seedSystemFolders();
        MailboxDtos.FolderView parent = service.create(principal, mailboxId,
                new MailboxDtos.SaveFolderRequest("Clients", null, null, null));
        MailboxDtos.FolderView child = service.create(principal, mailboxId,
                new MailboxDtos.SaveFolderRequest("Acme", parent.id(), null, null));

        MailFolder parentEntity = stored.stream().filter(f -> f.getId().equals(parent.id())).findFirst()
                .orElseThrow();
        when(folderRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(parent.id(), orgId))
                .thenReturn(Optional.of(parentEntity));

        assertThatThrownBy(() -> service.rename(principal, parent.id(),
                new MailboxDtos.SaveFolderRequest(null, child.id(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("its own sub-folders");
    }

    @Test
    @DisplayName("a system folder cannot be deleted")
    void systemFolderCannotBeDeleted() {
        seedSystemFolders();
        MailFolder trash = folderOfKind(MailEnums.FolderKind.TRASH);
        when(folderRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(trash.getId(), orgId))
                .thenReturn(Optional.of(trash));

        assertThatThrownBy(() -> service.delete(principal, trash.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be deleted");
        assertThat(trash.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("deleting a folder moves its threads to the inbox instead of losing them")
    void deletingAFolderKeepsItsMail() {
        seedSystemFolders();
        MailboxDtos.FolderView custom = service.create(principal, mailboxId,
                new MailboxDtos.SaveFolderRequest("Receipts", null, null, null));
        MailFolder customEntity = stored.stream().filter(f -> f.getId().equals(custom.id())).findFirst()
                .orElseThrow();
        MailFolder inbox = folderOfKind(MailEnums.FolderKind.INBOX);

        when(folderRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(custom.id(), orgId))
                .thenReturn(Optional.of(customEntity));
        when(folderRepository.existsByParentIdAndDeletedAtIsNull(custom.id())).thenReturn(false);
        when(folderRepository.findByMailboxIdAndKindAndDeletedAtIsNull(mailboxId, MailEnums.FolderKind.INBOX))
                .thenReturn(Optional.of(inbox));

        service.delete(principal, custom.id());

        verify(placementRepository).reassign(custom.id(), inbox.getId());
        assertThat(customEntity.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("a thread cannot be filed into another mailbox's folder")
    void movingAcrossMailboxesIsRefused() {
        seedSystemFolders();
        MailFolder archive = folderOfKind(MailEnums.FolderKind.ARCHIVE);
        when(folderRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(archive.getId(), orgId))
                .thenReturn(Optional.of(archive));

        MailThread elsewhere = new MailThread();
        elsewhere.setId(UUID.randomUUID());
        elsewhere.setOrganizationId(orgId);
        elsewhere.setMailboxId(UUID.randomUUID());
        when(access.requireThread(any(), eq(elsewhere.getId()))).thenReturn(elsewhere);

        assertThatThrownBy(() -> service.move(principal, archive.getId(), List.of(elsewhere.getId())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("its own mailbox");
        verify(placementRepository, never()).save(any());
    }

    @Test
    @DisplayName("a reply to an archived thread brings it back, but spam stays in spam")
    void inboundArrivalOnlyRescuesDiscardedThreads() {
        seedSystemFolders();
        MailFolder archive = folderOfKind(MailEnums.FolderKind.ARCHIVE);
        MailFolder spam = folderOfKind(MailEnums.FolderKind.SPAM);
        MailFolder inbox = folderOfKind(MailEnums.FolderKind.INBOX);
        when(folderRepository.findByMailboxIdAndKindAndDeletedAtIsNull(mailboxId, MailEnums.FolderKind.INBOX))
                .thenReturn(Optional.of(inbox));

        MailThread archived = thread();
        MailThreadFolder inArchive = placement(archived.getId(), archive.getId());
        when(placementRepository.findById(archived.getId())).thenReturn(Optional.of(inArchive));
        when(folderRepository.findById(archive.getId())).thenReturn(Optional.of(archive));

        service.fileInboundArrival(archived);
        assertThat(inArchive.getFolderId()).isEqualTo(inbox.getId());

        MailThread junk = thread();
        MailThreadFolder inSpam = placement(junk.getId(), spam.getId());
        when(placementRepository.findById(junk.getId())).thenReturn(Optional.of(inSpam));
        when(folderRepository.findById(spam.getId())).thenReturn(Optional.of(spam));

        service.fileInboundArrival(junk);
        assertThat(inSpam.getFolderId()).isEqualTo(spam.getId());
    }

    private MailThread thread() {
        MailThread t = new MailThread();
        t.setId(UUID.randomUUID());
        t.setOrganizationId(orgId);
        t.setMailboxId(mailboxId);
        return t;
    }

    private MailThreadFolder placement(UUID threadId, UUID folderId) {
        MailThreadFolder placement = new MailThreadFolder();
        placement.setThreadId(threadId);
        placement.setOrganizationId(orgId);
        placement.setFolderId(folderId);
        return placement;
    }

    private void seedSystemFolders() {
        when(folderRepository.existsByMailboxIdAndDeletedAtIsNull(mailboxId)).thenReturn(false);
        service.ensureSystemFolders(mailbox);
        when(folderRepository.existsByMailboxIdAndDeletedAtIsNull(mailboxId)).thenReturn(true);
    }

    private MailFolder folderOfKind(MailEnums.FolderKind kind) {
        return stored.stream().filter(f -> f.getKind() == kind).findFirst().orElseThrow();
    }
}
