package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadFlag;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The two reads a mail client does constantly: draw the sidebar, and list what is in a folder.
 */
@Service
@RequiredArgsConstructor
public class MailboxListService {

    private static final int MAX_PAGE = 100;

    private final MailThreadRepository threadRepository;
    private final MailMessageRepository messageRepository;
    private final MailFolderService folders;
    private final MailFlagService flags;
    private final MailboxAccess access;
    private final OrganizationMembershipRepository membershipRepository;

    /**
     * Every mailbox this person can open in the requested mode, each with its folders and unread counts.
     *
     * <p>One request draws the whole sidebar. A client that had to fetch mailboxes and then folders per
     * mailbox would spend its first second on round trips before showing anything.
     *
     * <p>Default is {@link MailboxAccess.Visibility#MINE}. Company mail is an explicit mode so a
     * holder of {@code MAIL_READ_ALL} does not see every organizational mailbox mixed into their
     * own inbox.
     */
    @Transactional
    public List<MailboxDtos.MailboxSummaryView> sidebar(PrabhixPrincipal principal,
                                                        MailboxAccess.Visibility visibility) {
        List<Mailbox> boxes = access.visibleMailboxes(principal, visibility);
        Map<UUID, OrganizationMembership> owners = ownerMemberships(principal.requireOrganizationId(), boxes);
        return boxes.stream()
                .map(m -> new MailboxDtos.MailboxSummaryView(
                        m.getId(), m.getAddress(), m.getName(), m.getKind(),
                        principal.userId().equals(m.getOwnerUserId()),
                        m.getOwnerUserId(),
                        ownerLabel(m, owners),
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
        Mailbox mailbox = access.requireMailbox(principal, thread.getMailboxId());
        access.recordAdminRead(principal, mailbox, threadId);
        UUID folderId = folders.foldersFor(List.of(threadId)).get(threadId);
        MailThreadFlag flag = flags.flagsFor(principal.userId(), List.of(threadId)).get(threadId);
        return flags.view(thread, folderId, flag);
    }

    /** Message bodies of a thread. This is the Mailroom read path. */
    @Transactional(readOnly = true)
    public List<MailboxDtos.MessageView> messages(PrabhixPrincipal principal, UUID threadId) {
        MailThread thread = access.requireThread(principal, threadId);
        Mailbox mailbox = access.requireMailbox(principal, thread.getMailboxId());
        access.recordAdminRead(principal, mailbox, threadId);
        return messageRepository.findByThreadIdAndDeletedAtIsNullOrderByOccurredAtAsc(threadId).stream()
                .map(this::toMessage)
                .toList();
    }

    /** Convenience for a client that only wants "mine", used by Mailroom's default view. */
    @Transactional(readOnly = true)
    public List<Mailbox> mine(PrabhixPrincipal principal) {
        return access.visibleMailboxes(principal).stream()
                .filter(m -> principal.userId().equals(m.getOwnerUserId()))
                .toList();
    }

    private Map<UUID, OrganizationMembership> ownerMemberships(UUID organizationId, List<Mailbox> boxes) {
        Set<UUID> ownerIds = boxes.stream()
                .map(Mailbox::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return membershipRepository.findByOrganizationIdAndUserIdIn(organizationId, ownerIds).stream()
                .collect(Collectors.toMap(OrganizationMembership::getUserId, m -> m, (a, b) -> a));
    }

    private static String ownerLabel(Mailbox mailbox, Map<UUID, OrganizationMembership> owners) {
        if (mailbox.getOwnerUserId() == null) {
            return "Shared inboxes";
        }
        OrganizationMembership membership = owners.get(mailbox.getOwnerUserId());
        return membership != null ? membership.getDisplayName() : "Unknown";
    }

    private MailboxDtos.MessageView toMessage(MailMessage m) {
        return new MailboxDtos.MessageView(
                m.getId(), m.getDirection(), m.getFromAddress(), m.getFromName(),
                m.getSubject(), m.getSnippet(), m.getBodyText(), m.getBodyHtml(),
                m.getDeliveryStatus(), m.getOccurredAt(), m.getAttachmentCount());
    }
}
