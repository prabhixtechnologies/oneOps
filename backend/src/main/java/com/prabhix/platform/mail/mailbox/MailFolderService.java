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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Folders: the places in a mailbox where threads are filed.
 *
 * <p>The six system folders are created for a mailbox the first time anybody asks for its folder list,
 * not only when the mailbox is created — mailboxes that predate V64 got theirs from the migration, but a
 * mailbox created by a code path that does not know about folders would otherwise have none, and an empty
 * sidebar is a worse failure than a lazy insert.
 */
@Service
@RequiredArgsConstructor
public class MailFolderService {

    /** Name and position of each system folder. Order matches what a mail client shows. */
    private static final List<SystemFolder> SYSTEM = List.of(
            new SystemFolder(MailEnums.FolderKind.INBOX, "Inbox", 10),
            new SystemFolder(MailEnums.FolderKind.SENT, "Sent", 20),
            new SystemFolder(MailEnums.FolderKind.DRAFTS, "Drafts", 30),
            new SystemFolder(MailEnums.FolderKind.ARCHIVE, "Archive", 40),
            new SystemFolder(MailEnums.FolderKind.SPAM, "Spam", 50),
            new SystemFolder(MailEnums.FolderKind.TRASH, "Trash", 60));

    private static final int MAX_DEPTH = 3;
    private static final int MAX_CUSTOM_FOLDERS = 200;

    private final MailFolderRepository folderRepository;
    private final MailThreadFolderRepository placementRepository;
    private final MailboxAccess access;

    @Transactional
    public List<MailboxDtos.FolderView> list(PrabhixPrincipal principal, UUID mailboxId) {
        Mailbox mailbox = access.requireMailbox(principal, mailboxId);
        List<MailFolder> folders = ensureSystemFolders(mailbox);

        Map<UUID, long[]> counts = new HashMap<>();
        if (!folders.isEmpty()) {
            for (Object[] row : folderRepository.countsByFolder(
                    folders.stream().map(MailFolder::getId).toList(), principal.userId())) {
                long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                long unread = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                counts.put((UUID) row[0], new long[]{total, unread});
            }
        }

        return folders.stream()
                .map(f -> {
                    long[] c = counts.getOrDefault(f.getId(), new long[]{0L, 0L});
                    return new MailboxDtos.FolderView(f.getId(), f.getMailboxId(), f.getKind(), f.getName(),
                            f.getParentId(), f.getSortOrder(), f.getColour(), c[0], c[1]);
                })
                .toList();
    }

    /**
     * Creates the system folders for a mailbox if it has none, and returns the full list either way.
     *
     * <p>Called on every folder list, so it has to be cheap in the common case: one exists-check, and no
     * writes at all once the folders are there.
     */
    @Transactional
    public List<MailFolder> ensureSystemFolders(Mailbox mailbox) {
        if (!folderRepository.existsByMailboxIdAndDeletedAtIsNull(mailbox.getId())) {
            for (SystemFolder spec : SYSTEM) {
                MailFolder folder = new MailFolder();
                folder.setOrganizationId(mailbox.getOrganizationId());
                folder.setMailboxId(mailbox.getId());
                folder.setKind(spec.kind());
                folder.setName(spec.name());
                folder.setSortOrder(spec.sortOrder());
                folderRepository.save(folder);
            }
        }
        return folderRepository.findByMailboxIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(mailbox.getId());
    }

    @Transactional
    public MailboxDtos.FolderView create(PrabhixPrincipal principal, UUID mailboxId,
                                         MailboxDtos.SaveFolderRequest request) {
        Mailbox mailbox = access.requireMailbox(principal, mailboxId);
        ensureSystemFolders(mailbox);

        String name = requireName(request.name());
        List<MailFolder> existing = folderRepository
                .findByMailboxIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(mailboxId);
        long custom = existing.stream().filter(f -> f.getKind() == MailEnums.FolderKind.CUSTOM).count();
        if (custom >= MAX_CUSTOM_FOLDERS) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.LIMIT_EXCEEDED,
                    "This mailbox already has the maximum number of folders");
        }

        UUID parentId = resolveParent(existing, request.parentId(), null);
        assertNameFree(existing, name, parentId, null);

        MailFolder folder = new MailFolder();
        folder.setOrganizationId(mailbox.getOrganizationId());
        folder.setMailboxId(mailboxId);
        folder.setKind(MailEnums.FolderKind.CUSTOM);
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 100);
        folder.setColour(request.colour());
        return toView(folderRepository.save(folder), 0L, 0L);
    }

    @Transactional
    public MailboxDtos.FolderView rename(PrabhixPrincipal principal, UUID folderId,
                                         MailboxDtos.SaveFolderRequest request) {
        MailFolder folder = requireFolder(principal, folderId);
        List<MailFolder> siblings = folderRepository
                .findByMailboxIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(folder.getMailboxId());

        if (request.name() != null) {
            String name = requireName(request.name());
            // A system folder may be renamed — "Bin" instead of "Trash", or a translation — because the
            // kind is what code matches on and the name is only shown to a person.
            assertNameFree(siblings, name, folder.getParentId(), folder.getId());
            folder.setName(name);
        }
        if (request.colour() != null) {
            folder.setColour(request.colour().isBlank() ? null : request.colour());
        }
        if (request.sortOrder() != null) {
            folder.setSortOrder(request.sortOrder());
        }
        if (request.parentId() != null) {
            if (folder.getKind().isSystem()) {
                throw ApiException.invalidState("A system folder cannot be moved under another folder");
            }
            folder.setParentId(resolveParent(siblings, request.parentId(), folder.getId()));
        }
        return toView(folderRepository.save(folder), 0L, 0L);
    }

    /**
     * Deletes a custom folder, moving whatever was in it to the inbox.
     *
     * <p>The threads are deliberately not deleted with it. A folder is a place, and destroying a place
     * should not destroy what was standing in it — that is the difference between deleting a folder and
     * deleting mail, and getting it wrong loses somebody's correspondence.
     */
    @Transactional
    public void delete(PrabhixPrincipal principal, UUID folderId) {
        MailFolder folder = requireFolder(principal, folderId);
        if (folder.getKind().isSystem()) {
            throw ApiException.invalidState("The " + folder.getName() + " folder cannot be deleted");
        }
        if (folderRepository.existsByParentIdAndDeletedAtIsNull(folderId)) {
            throw ApiException.invalidState("Empty this folder's sub-folders first");
        }
        MailFolder inbox = systemFolder(folder.getMailboxId(), MailEnums.FolderKind.INBOX);
        placementRepository.reassign(folderId, inbox.getId());
        folder.setDeletedAt(Instant.now());
        folderRepository.save(folder);
    }

    /** Files a set of threads into a folder. Returns how many actually moved. */
    @Transactional
    public int move(PrabhixPrincipal principal, UUID folderId, List<UUID> threadIds) {
        MailFolder folder = requireFolder(principal, folderId);
        int moved = 0;
        for (UUID threadId : threadIds) {
            MailThread thread;
            try {
                thread = access.requireThread(principal, threadId);
            } catch (ApiException skip) {
                continue;
            }
            if (!thread.getMailboxId().equals(folder.getMailboxId())) {
                // Moving a thread between mailboxes is a transfer, not a file: it changes who can read it
                // and which address replies come from. That is a different operation and it is not this
                // one.
                throw ApiException.invalidState("A thread can only be moved within its own mailbox");
            }
            place(thread, folder.getId(), principal.userId());
            moved++;
        }
        return moved;
    }

    /**
     * Where a new thread starts life. Called by ingestion for inbound mail and by compose for outbound,
     * so that every thread has a folder from the moment it exists rather than appearing nowhere until
     * somebody moves it.
     */
    @Transactional
    public void fileNewThread(MailThread thread, MailEnums.FolderKind kind) {
        Optional<MailFolder> folder = folderRepository.findByMailboxIdAndKindAndDeletedAtIsNull(
                thread.getMailboxId(), kind);
        if (folder.isEmpty()) {
            // A mailbox with no folders yet: the next folder list will create them and the backfill in
            // ensureSystemFolders leaves this thread in the inbox, so there is nothing to repair here.
            return;
        }
        place(thread, folder.get().getId(), null);
    }

    /**
     * Files a thread that has just received a message.
     *
     * <p>Not the same rule as a new thread. An unplaced thread goes to the inbox. A thread the reader had
     * archived or thrown away comes back, because somebody replying to a conversation you discarded is
     * exactly the case where you want to see it again. A thread in Spam stays in Spam — that judgement was
     * about the sender and one more message from them does not overturn it — and a thread already in a
     * folder somebody chose stays where they put it.
     */
    @Transactional
    public void fileInboundArrival(MailThread thread) {
        Optional<MailThreadFolder> current = placementRepository.findById(thread.getId());
        if (current.isPresent()) {
            Optional<MailFolder> folder = folderRepository.findById(current.get().getFolderId());
            boolean discarded = folder
                    .map(f -> f.getKind() == MailEnums.FolderKind.ARCHIVE
                            || f.getKind() == MailEnums.FolderKind.TRASH)
                    .orElse(true);
            if (!discarded) {
                return;
            }
        }
        fileNewThread(thread, MailEnums.FolderKind.INBOX);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> foldersFor(List<UUID> threadIds) {
        if (threadIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> byThread = new LinkedHashMap<>();
        for (MailThreadFolder placement : placementRepository.findByThreadIdIn(threadIds)) {
            byThread.put(placement.getThreadId(), placement.getFolderId());
        }
        return byThread;
    }

    private void place(MailThread thread, UUID folderId, UUID actorId) {
        MailThreadFolder placement = placementRepository.findById(thread.getId())
                .orElseGet(() -> {
                    MailThreadFolder fresh = new MailThreadFolder();
                    fresh.setThreadId(thread.getId());
                    fresh.setOrganizationId(thread.getOrganizationId());
                    return fresh;
                });
        placement.setFolderId(folderId);
        placement.setMovedAt(Instant.now());
        placement.setMovedBy(actorId);
        placementRepository.save(placement);
    }

    private MailFolder systemFolder(UUID mailboxId, MailEnums.FolderKind kind) {
        return folderRepository.findByMailboxIdAndKindAndDeletedAtIsNull(mailboxId, kind)
                .orElseThrow(() -> ApiException.invalidState(
                        "This mailbox has no " + kind.name().toLowerCase(Locale.ROOT) + " folder"));
    }

    private MailFolder requireFolder(PrabhixPrincipal principal, UUID folderId) {
        MailFolder folder = folderRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(
                        folderId, principal.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Folder"));
        access.requireMailbox(principal, folder.getMailboxId());
        return folder;
    }

    private String requireName(String raw) {
        String name = raw != null ? raw.trim() : "";
        if (name.isEmpty()) {
            throw ApiException.invalidState("A folder needs a name");
        }
        if (name.length() > 120) {
            throw ApiException.invalidState("That folder name is too long");
        }
        return name;
    }

    private void assertNameFree(List<MailFolder> siblings, String name, UUID parentId, UUID excludeId) {
        boolean taken = siblings.stream()
                .filter(f -> !f.getId().equals(excludeId))
                .filter(f -> java.util.Objects.equals(f.getParentId(), parentId))
                .anyMatch(f -> f.getName().equalsIgnoreCase(name));
        if (taken) {
            throw ApiException.conflict("A folder called " + name + " is already here");
        }
    }

    /**
     * Validates a proposed parent: it has to be in the same mailbox, it cannot be the folder itself, and
     * it cannot be one of the folder's own descendants — that last one would build a cycle the sidebar
     * would recurse into forever.
     */
    private UUID resolveParent(List<MailFolder> siblings, UUID parentId, UUID selfId) {
        if (parentId == null) {
            return null;
        }
        MailFolder parent = siblings.stream()
                .filter(f -> f.getId().equals(parentId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Parent folder"));
        if (parent.getId().equals(selfId)) {
            throw ApiException.invalidState("A folder cannot be inside itself");
        }

        Map<UUID, MailFolder> byId = new HashMap<>();
        siblings.forEach(f -> byId.put(f.getId(), f));

        List<UUID> ancestors = new ArrayList<>();
        MailFolder walk = parent;
        while (walk != null && ancestors.size() <= MAX_DEPTH) {
            if (selfId != null && walk.getId().equals(selfId)) {
                throw ApiException.invalidState("A folder cannot be inside one of its own sub-folders");
            }
            ancestors.add(walk.getId());
            walk = walk.getParentId() != null ? byId.get(walk.getParentId()) : null;
        }
        if (ancestors.size() >= MAX_DEPTH) {
            throw ApiException.invalidState("Folders can only be nested " + MAX_DEPTH + " deep");
        }
        return parent.getId();
    }

    private MailboxDtos.FolderView toView(MailFolder f, long total, long unread) {
        return new MailboxDtos.FolderView(f.getId(), f.getMailboxId(), f.getKind(), f.getName(),
                f.getParentId(), f.getSortOrder(), f.getColour(), total, unread);
    }

    private record SystemFolder(MailEnums.FolderKind kind, String name, int sortOrder) {
    }
}
