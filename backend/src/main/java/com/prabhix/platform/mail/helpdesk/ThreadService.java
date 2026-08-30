package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.*;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.mailbox.MailboxAccess;
import com.prabhix.platform.mail.repository.*;
import com.prabhix.platform.mail.util.ThreadCursor;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ThreadService {

    private final MailThreadRepository threadRepository;
    private final MailMessageRepository messageRepository;
    private final MailThreadNoteRepository noteRepository;
    private final MailThreadEventRepository eventRepository;
    private final MailThreadTagRepository threadTagRepository;
    private final MailboxRepository mailboxRepository;
    private final MailboxAccess mailboxAccess;
    private final SlaService slaService;
    private final PrabhixProperties properties;

    /**
     * Statuses that take a thread out of the queue.
     *
     * <p>SPAM and TRASH count, which is the point of naming the set rather than testing for RESOLVED:
     * a thread binned as spam is no longer open work, and leaving it in {@code open_thread_count}
     * makes the mailbox badge permanently overstate the backlog.
     */
    private static final java.util.Set<MailEnums.ThreadStatus> CLOSED_STATUSES = java.util.EnumSet.of(
            MailEnums.ThreadStatus.RESOLVED,
            MailEnums.ThreadStatus.CLOSED,
            MailEnums.ThreadStatus.SPAM,
            MailEnums.ThreadStatus.TRASH);

    @Transactional(readOnly = true)
    public CursorPage<ThreadDtos.ThreadSummary> list(PrabhixPrincipal principal,
                                                     ThreadDtos.ThreadListQuery query) {
        UUID orgId = principal.requireOrganizationId();
        boolean readAll = principal.has(Permission.MAIL_READ_ALL);
        UUID[] mailboxIds = resolveMailboxIds(principal, readAll);

        Cursor cursor = query.cursor() != null ? ThreadCursor.decode(query.cursor()) : Cursor.beginning();
        int limit = properties.limits().clampPageSize(query.limit()) + 1;

        List<MailThread> fetched;
        if (query.q() != null && !query.q().isBlank()) {
            fetched = threadRepository.searchWithCursor(orgId, readAll, mailboxIds, query.tagId(),
                    query.q(), cursor.timestamp(), cursor.id(), limit);
        } else {
            fetched = threadRepository.listWithCursor(
                    orgId, query.mailboxId(),
                    query.status() != null ? query.status().name() : null,
                    query.priority() != null ? query.priority().name() : null,
                    query.assigneeUserId(), query.assigneeTeamId(), query.tagId(),
                    query.unreadOnly(), query.hasAttachment(),
                    readAll, mailboxIds,
                    cursor.timestamp(), cursor.id(), limit);
        }

        // Tags for the whole page in one query, before the per-row mapper runs. Resolving them inside
        // toSummary would be a query per thread.
        var tagsByThread = tagsFor(fetched.stream().map(MailThread::getId).toList());
        return CursorPage.of(fetched, limit - 1,
                t -> toSummary(t, tagsByThread.getOrDefault(t.getId(), List.of())),
                ThreadCursor::encode);
    }

    @Transactional(readOnly = true)
    public ThreadDtos.ThreadDetail get(PrabhixPrincipal principal, UUID threadId) {
        UUID orgId = principal.requireOrganizationId();
        MailThread thread = threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(threadId, orgId)
                .orElseThrow(() -> ApiException.notFound("Thread"));
        assertVisible(principal, thread);

        var messages = messageRepository.findByThreadIdAndDeletedAtIsNullOrderByOccurredAtAsc(threadId)
                .stream().map(this::toMessage).toList();
        var notes = noteRepository.findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId)
                .stream().map(this::toNote).toList();
        var events = eventRepository.findByThreadIdOrderByCreatedAtAsc(threadId)
                .stream().map(this::toEvent).toList();

        return new ThreadDtos.ThreadDetail(
                toSummary(thread, tagsFor(List.of(threadId)).getOrDefault(threadId, List.of())),
                messages, notes, events);
    }

    @Transactional
    public ThreadDtos.ThreadSummary update(PrabhixPrincipal principal, UUID threadId,
                                           ThreadDtos.UpdateThreadRequest request) {
        MailThread thread = loadVisible(principal, threadId);

        if (request.status() != null && request.status() != thread.getStatus()) {
            MailEnums.ThreadStatus from = thread.getStatus();
            MailEnums.ThreadStatus to = request.status();
            thread.setStatus(to);

            if (to == MailEnums.ThreadStatus.PENDING_CUSTOMER) {
                slaService.pauseIfPendingCustomer(thread);
            }
            applyResolution(thread, from, to, principal);
            appendEvent(thread, MailEnums.ThreadEventType.STATUS_CHANGED,
                    from.name(), to.name(), principal);
        }

        if (request.priority() != null && request.priority() != thread.getPriority()) {
            MailEnums.Priority from = thread.getPriority();
            thread.setPriority(request.priority());
            appendEvent(thread, MailEnums.ThreadEventType.PRIORITY_CHANGED,
                    from.name(), request.priority().name(), principal);
        }

        MailThread saved = threadRepository.save(thread);
        return toSummary(saved, tagsFor(List.of(saved.getId())).getOrDefault(saved.getId(), List.of()));
    }

    /**
     * Records who closed a thread and when, and keeps the mailbox's open counter honest.
     *
     * <p>Both halves were missing rather than wrong. {@code resolved_at} and {@code resolved_by} were
     * columns that no code ever wrote, so "when was this resolved and by whom" was unanswerable
     * except by reading the event log. And {@code open_thread_count} was incremented on the first
     * inbound message and never decremented, so it only ever grew: a mailbox that had handled
     * everything still showed its lifetime total as open work.
     *
     * <p>Reopening is handled too, in the same place, because a resolve that cannot be undone
     * symmetrically is how the counter drifted in the first place.
     */
    private void applyResolution(MailThread thread,
                                 MailEnums.ThreadStatus from,
                                 MailEnums.ThreadStatus to,
                                 PrabhixPrincipal principal) {
        boolean wasClosed = CLOSED_STATUSES.contains(from);
        boolean nowClosed = CLOSED_STATUSES.contains(to);
        if (wasClosed == nowClosed) {
            return;
        }

        if (nowClosed) {
            thread.setResolvedAt(Instant.now());
            thread.setResolvedBy(principal.userId());
        } else {
            thread.setResolvedAt(null);
            thread.setResolvedBy(null);
        }

        mailboxRepository.findById(thread.getMailboxId()).ifPresent(mb -> {
            int current = mb.getOpenThreadCount();
            // Floored rather than allowed negative: the counter is denormalised, so it can already be
            // behind reality on data that predates this, and a negative badge is a worse lie than a
            // stale zero.
            mb.setOpenThreadCount(nowClosed ? Math.max(0, current - 1) : current + 1);
            mailboxRepository.save(mb);
        });
    }

    private void appendEvent(MailThread thread, MailEnums.ThreadEventType type,
                             String fromValue, String toValue, PrabhixPrincipal principal) {
        MailThreadEvent event = new MailThreadEvent();
        event.setOrganizationId(thread.getOrganizationId());
        event.setThreadId(thread.getId());
        event.setEventType(type);
        event.setFromValue(fromValue);
        event.setToValue(toValue);
        event.setActorUserId(principal.userId());
        eventRepository.save(event);
    }

    @Transactional
    public int bulkUpdate(PrabhixPrincipal principal, ThreadDtos.BulkUpdateRequest request) {
        List<UUID> allowed = new ArrayList<>();
        for (UUID id : request.threadIds()) {
            try {
                loadVisible(principal, id);
                allowed.add(id);
            } catch (ApiException ignored) {
                // skip threads the caller cannot access
            }
        }
        if (allowed.isEmpty()) {
            return 0;
        }

        UUID orgId = principal.requireOrganizationId();
        int count = 0;
        if (request.status() != null) {
            count = threadRepository.bulkUpdateStatus(orgId, allowed, request.status());
            if (request.status() == MailEnums.ThreadStatus.PENDING_CUSTOMER) {
                threadRepository.bulkPauseSla(orgId, allowed, Instant.now());
            }
        }
        if (request.priority() != null) {
            threadRepository.bulkUpdatePriority(orgId, allowed, request.priority());
        }
        return count > 0 ? count : allowed.size();
    }

    private MailThread loadVisible(PrabhixPrincipal principal, UUID threadId) {
        MailThread thread = threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(
                threadId, principal.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Thread"));
        assertVisible(principal, thread);
        return thread;
    }

    private void assertVisible(PrabhixPrincipal principal, MailThread thread) {
        if (principal.has(Permission.MAIL_READ_ALL)) {
            return;
        }
        UUID[] ids = resolveMailboxIds(principal, false);
        for (UUID id : ids) {
            if (id.equals(thread.getMailboxId())) {
                return;
            }
        }
        throw ApiException.forbidden("You do not have access to this thread");
    }

    /**
     * Delegated to {@link MailboxAccess} rather than resolved here.
     *
     * <p>This method used to duplicate the access rule and pass an empty team list, so the helpdesk
     * queue and the mail client disagreed about which mailboxes a person could see the moment access
     * came from a team. One of the two answers had to be authoritative; it is the one that also
     * guards folders, flags, drafts and compose.
     */
    private UUID[] resolveMailboxIds(PrabhixPrincipal principal, boolean readAll) {
        if (readAll) {
            return new UUID[0];
        }
        return mailboxAccess.accessibleMailboxIds(principal).toArray(UUID[]::new);
    }

    /**
     * The tags on each of the given threads, keyed by thread id.
     *
     * <p>One query for the whole page. Callers pass every id they are about to map, so the mapper can
     * stay a pure function of a thread and a list.
     */
    private java.util.Map<UUID, List<ThreadDtos.TagRef>> tagsFor(List<UUID> threadIds) {
        if (threadIds.isEmpty()) {
            return java.util.Map.of();
        }
        var byThread = new java.util.HashMap<UUID, List<ThreadDtos.TagRef>>();
        for (var row : threadTagRepository.findTagsForThreads(threadIds)) {
            byThread.computeIfAbsent(row.getThreadId(), k -> new ArrayList<>())
                    .add(new ThreadDtos.TagRef(row.getTagId(), row.getSlug(), row.getName(),
                            row.getColour()));
        }
        return byThread;
    }

    private ThreadDtos.ThreadSummary toSummary(MailThread t, List<ThreadDtos.TagRef> tags) {
        return new ThreadDtos.ThreadSummary(
                t.getId(), t.getMailboxId(), t.getReferenceKey(), t.getSubject(),
                t.getStatus(), t.getPriority(), t.getAssigneeUserId(), t.getAssigneeTeamId(),
                t.getCustomerEmail(), t.getSnippet(), t.getMessageCount(), t.getUnreadCount(),
                t.isHasAttachments(), t.getLastMessageAt(), t.getLastMessageDirection(),
                t.getSlaDueAt(), t.getSlaBreachedAt(), t.getFirstResponseAt(), t.getResolvedAt(),
                tags);
    }

    private ThreadDtos.MessageSummary toMessage(MailMessage m) {
        return new ThreadDtos.MessageSummary(
                m.getId(), m.getDirection(), m.getFromAddress(), m.getFromName(),
                m.getSubject(), m.getSnippet(), m.getBodyText(), m.getBodyHtml(),
                m.getDeliveryStatus(), m.getOccurredAt(), m.getAttachmentCount());
    }

    private ThreadDtos.NoteSummary toNote(MailThreadNote n) {
        return new ThreadDtos.NoteSummary(n.getId(), n.getAuthorUserId(), n.getBodyHtml(), n.getCreatedAt());
    }

    private ThreadDtos.EventSummary toEvent(MailThreadEvent e) {
        return new ThreadDtos.EventSummary(e.getEventType(), e.getActorUserId(), e.getActorLabel(),
                e.getFromValue(), e.getToValue(), e.getCreatedAt());
    }
}
