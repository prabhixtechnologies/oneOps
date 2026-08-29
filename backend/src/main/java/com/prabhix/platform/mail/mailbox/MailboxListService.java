package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadFlag;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two reads a mail client does constantly: draw the sidebar, and list what is in a folder.
 */
@Service
@RequiredArgsConstructor
public class MailboxListService {

    private static final int MAX_PAGE = 100;

    private final MailThreadRepository threadRepository;
    private final MailFolderService folders;
    private final MailFlagService flags;
    private final MailboxAccess access;

    /**
     * Every mailbox this person can open, each with its folders and unread counts.
     *
     * <p>One request draws the whole sidebar. A client that had to fetch mailboxes and then folders per
     * mailbox would spend its first second on round trips before showing anything.
     */
    @Transactional
    public List<MailboxDtos.MailboxSummaryView> sidebar(PrabhixPrincipal principal) {
        return access.visibleMailboxes(principal).stream()
                .map(m -> new MailboxDtos.MailboxSummaryView(
                        m.getId(), m.getAddress(), m.getName(), m.getKind(),
                        principal.userId().equals(m.getOwnerUserId()),
                        folders.list(principal, m.getId())))
                .toList();
    }

    /** What is in a folder, newest activity first. */
    @Transactional(readOnly = true)
    public List<MailboxDtos.MailThreadView> threadsIn(PrabhixPrincipal principal, UUID folderId,
                                                      Integer limit, Integer offset) {
        int size = limit != null ? Math.min(Math.max(limit, 1), MAX_PAGE) : 50;
        int skip = offset != null ? Math.max(offset, 0) : 0;

        List<MailThread> threads = threadRepository.findInFolder(
                principal.requireOrganizationId(), folderId, size, skip);
        if (threads.isEmpty()) {
            return List.of();
        }
        // The folder was reached by id, so access is checked against the first thread's mailbox — all
        // threads in a folder are in the same mailbox by construction, enforced in MailFolderService.move.
        access.requireMailbox(principal, threads.get(0).getMailboxId());

        List<UUID> ids = threads.stream().map(MailThread::getId).toList();
        Map<UUID, MailThreadFlag> byThread = flags.flagsFor(principal.userId(), ids);
        return threads.stream()
                .map(t -> flags.view(t, folderId, byThread.get(t.getId())))
                .toList();
    }

    /** A single thread as a mail client sees it, with the reader's own flags. */
    @Transactional(readOnly = true)
    public MailboxDtos.MailThreadView thread(PrabhixPrincipal principal, UUID threadId) {
        MailThread thread = access.requireThread(principal, threadId);
        UUID folderId = folders.foldersFor(List.of(threadId)).get(threadId);
        MailThreadFlag flag = flags.flagsFor(principal.userId(), List.of(threadId)).get(threadId);
        return flags.view(thread, folderId, flag);
    }

    /** Convenience for a client that only wants "mine", used by Mailroom's default view. */
    @Transactional(readOnly = true)
    public List<Mailbox> mine(PrabhixPrincipal principal) {
        return access.visibleMailboxes(principal).stream()
                .filter(m -> principal.userId().equals(m.getOwnerUserId()))
                .toList();
    }
}
