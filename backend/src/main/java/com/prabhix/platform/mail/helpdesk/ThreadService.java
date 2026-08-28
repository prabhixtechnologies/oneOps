package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.*;
import com.prabhix.platform.mail.dto.ThreadDtos;
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
    private final MailboxMemberRepository memberRepository;
    private final SlaService slaService;
    private final PrabhixProperties properties;

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

        return CursorPage.of(fetched, limit - 1, this::toSummary, ThreadCursor::encode);
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

        return new ThreadDtos.ThreadDetail(toSummary(thread), messages, notes, events);
    }

    @Transactional
    public ThreadDtos.ThreadSummary update(PrabhixPrincipal principal, UUID threadId,
                                           ThreadDtos.UpdateThreadRequest request) {
        MailThread thread = loadVisible(principal, threadId);
        if (request.status() != null) {
            thread.setStatus(request.status());
            if (request.status() == MailEnums.ThreadStatus.PENDING_CUSTOMER) {
                slaService.pauseIfPendingCustomer(thread);
            }
        }
        if (request.priority() != null) {
            thread.setPriority(request.priority());
        }
        return toSummary(threadRepository.save(thread));
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

    private UUID[] resolveMailboxIds(PrabhixPrincipal principal, boolean readAll) {
        if (readAll) {
            return new UUID[0];
        }
        List<UUID> ids = memberRepository.findAccessibleMailboxIds(
                principal.requireOrganizationId(), principal.userId(), List.of());
        return ids.toArray(UUID[]::new);
    }

    private ThreadDtos.ThreadSummary toSummary(MailThread t) {
        return new ThreadDtos.ThreadSummary(
                t.getId(), t.getMailboxId(), t.getReferenceKey(), t.getSubject(),
                t.getStatus(), t.getPriority(), t.getAssigneeUserId(), t.getAssigneeTeamId(),
                t.getCustomerEmail(), t.getSnippet(), t.getMessageCount(), t.getUnreadCount(),
                t.isHasAttachments(), t.getLastMessageAt(), t.getLastMessageDirection(),
                t.getSlaDueAt(), t.getSlaBreachedAt());
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
