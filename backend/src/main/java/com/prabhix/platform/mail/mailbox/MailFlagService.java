package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadFlag;
import com.prabhix.platform.mail.repository.MailThreadFlagRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read, starred and snoozed, per person.
 *
 * <p>All three are facts about a reader rather than about a thread, which is why they are not columns on
 * {@code mail_threads}: a shared mailbox with four people in it has four correct answers to "is this
 * unread", and a single column can only hold one of them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailFlagService {

    private static final int MAX_BULK = 500;

    private final MailThreadFlagRepository flagRepository;
    private final MailThreadRepository threadRepository;
    private final MailFolderService folders;
    private final MailboxAccess access;

    @Transactional
    public MailboxDtos.MailThreadView apply(PrabhixPrincipal principal, UUID threadId,
                                            MailboxDtos.FlagRequest request) {
        MailThread thread = access.requireThread(principal, threadId);
        MailThreadFlag flag = mutate(thread, principal.userId(), request.read(), request.starred(),
                request.snoozeUntil());
        UUID folderId = folders.foldersFor(List.of(threadId)).get(threadId);
        return view(thread, folderId, flag);
    }

    @Transactional
    public int applyBulk(PrabhixPrincipal principal, MailboxDtos.BulkFlagRequest request) {
        if (request.threadIds().size() > MAX_BULK) {
            throw ApiException.invalidState("Select at most " + MAX_BULK + " conversations at once");
        }
        int touched = 0;
        for (UUID threadId : request.threadIds()) {
            try {
                MailThread thread = access.requireThread(principal, threadId);
                mutate(thread, principal.userId(), request.read(), request.starred(), request.snoozeUntil());
                touched++;
            } catch (ApiException skip) {
                // A selection can outlive a permission change or a delete. Skipping is the right answer:
                // failing the whole batch because one row moved would make bulk actions unusable.
            }
        }
        return touched;
    }

    /**
     * The reader's flags for a page of threads, in one query.
     *
     * <p>Threads with no row yet are absent from the map, which the caller reads as unread and unstarred
     * — the correct default, and the reason a flag row is only written when somebody changes something
     * rather than for every thread that has ever been delivered.
     */
    @Transactional(readOnly = true)
    public Map<UUID, MailThreadFlag> flagsFor(UUID userId, List<UUID> threadIds) {
        if (threadIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MailThreadFlag> byThread = new HashMap<>();
        for (MailThreadFlag flag : flagRepository.findForThreads(userId, threadIds)) {
            byThread.put(flag.getId().getThreadId(), flag);
        }
        return byThread;
    }

    @Transactional(readOnly = true)
    public List<MailboxDtos.MailThreadView> starred(PrabhixPrincipal principal) {
        List<MailThreadFlag> flags = flagRepository.findStarred(
                principal.requireOrganizationId(), principal.userId());
        if (flags.isEmpty()) {
            return List.of();
        }
        List<UUID> threadIds = flags.stream().map(f -> f.getId().getThreadId()).toList();
        Map<UUID, UUID> placements = folders.foldersFor(threadIds);
        Map<UUID, MailThreadFlag> byThread = new HashMap<>();
        flags.forEach(f -> byThread.put(f.getId().getThreadId(), f));

        return threadRepository.findAllById(threadIds).stream()
                .filter(t -> t.getDeletedAt() == null)
                .map(t -> view(t, placements.get(t.getId()), byThread.get(t.getId())))
                .toList();
    }

    /**
     * Returns snoozed threads to the inbox once their time arrives.
     *
     * <p>A minute of lateness is invisible to a person and a tight loop is not, so this runs on the same
     * cadence as the other mail sweeps rather than scheduling a job per snooze.
     */
    @Scheduled(fixedDelayString = "${prabhix.mail.snooze-sweep-interval:PT60S}")
    @Transactional
    public void wakeSnoozed() {
        List<MailThreadFlag> due = flagRepository.findDueSnoozes(Instant.now());
        if (due.isEmpty()) {
            return;
        }
        for (MailThreadFlag flag : due) {
            flag.setSnoozedUntil(null);
            flag.setReadAt(null);
            flag.setUpdatedAt(Instant.now());
            flagRepository.save(flag);
            threadRepository.findById(flag.getId().getThreadId())
                    .filter(t -> t.getDeletedAt() == null)
                    .ifPresent(t -> folders.fileNewThread(t, MailEnums.FolderKind.INBOX));
        }
        log.info("Returned {} snoozed conversations to the inbox", due.size());
    }

    private MailThreadFlag mutate(MailThread thread, UUID userId, Boolean read, Boolean starred,
                                  Instant snoozeUntil) {
        MailThreadFlag flag = flagRepository.findByIdThreadIdAndIdUserId(thread.getId(), userId)
                .orElseGet(() -> MailThreadFlag.of(thread.getId(), userId, thread.getOrganizationId()));

        if (read != null) {
            flag.setReadAt(read ? Instant.now() : null);
        }
        if (starred != null) {
            flag.setStarredAt(starred ? Instant.now() : null);
        }
        if (snoozeUntil != null) {
            if (snoozeUntil.isBefore(Instant.now())) {
                throw ApiException.invalidState("Pick a time in the future to snooze until");
            }
            flag.setSnoozedUntil(snoozeUntil);
        }
        flag.setUpdatedAt(Instant.now());
        return flagRepository.save(flag);
    }

    MailboxDtos.MailThreadView view(MailThread thread, UUID folderId, MailThreadFlag flag) {
        return new MailboxDtos.MailThreadView(
                thread.getId(), thread.getMailboxId(), folderId,
                thread.getSubject(), thread.getSnippet(),
                thread.getCustomerEmail(), thread.getCustomerName(),
                thread.getMessageCount(), thread.isHasAttachments(),
                flag != null && flag.isRead(),
                flag != null && flag.isStarred(),
                flag != null ? flag.getSnoozedUntil() : null,
                thread.getLastMessageAt(), thread.getLastMessageDirection());
    }
}
