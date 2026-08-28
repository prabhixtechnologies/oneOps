package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailThreadEvent;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailThreadEventRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final MailThreadRepository threadRepository;
    private final MailboxRepository mailboxRepository;
    private final MailThreadEventRepository eventRepository;

    @Transactional
    public void assignToUser(UUID organizationId, UUID threadId, UUID userId, UUID assignedBy) {
        MailThread thread = requireThread(organizationId, threadId);
        boolean wasUnassigned = thread.getAssigneeUserId() == null && thread.getAssigneeTeamId() == null;
        thread.setAssigneeUserId(userId);
        thread.setAssigneeTeamId(null);
        thread.setAssignedAt(Instant.now());
        thread.setAssignedBy(assignedBy);
        threadRepository.save(thread);
        if (wasUnassigned) {
            decrementUnassigned(thread.getMailboxId());
        }
        appendEvent(thread, MailEnums.ThreadEventType.ASSIGNED, userId.toString());
    }

    @Transactional
    public void assignToTeam(UUID organizationId, UUID threadId, UUID teamId, UUID assignedBy) {
        MailThread thread = requireThread(organizationId, threadId);
        boolean wasUnassigned = thread.getAssigneeUserId() == null && thread.getAssigneeTeamId() == null;
        thread.setAssigneeTeamId(teamId);
        thread.setAssigneeUserId(null);
        thread.setAssignedAt(Instant.now());
        thread.setAssignedBy(assignedBy);
        threadRepository.save(thread);
        if (wasUnassigned) {
            decrementUnassigned(thread.getMailboxId());
        }
        appendEvent(thread, MailEnums.ThreadEventType.ASSIGNED, teamId.toString());
    }

    @Transactional
    public void unassign(UUID organizationId, UUID threadId) {
        MailThread thread = requireThread(organizationId, threadId);
        thread.setAssigneeUserId(null);
        thread.setAssigneeTeamId(null);
        thread.setAssignedAt(null);
        thread.setAssignedBy(null);
        threadRepository.save(thread);
        incrementUnassigned(thread.getMailboxId());
        appendEvent(thread, MailEnums.ThreadEventType.UNASSIGNED, null);
    }

    @Transactional
    public void claimIfUnassigned(UUID threadId, UUID userId) {
        int updated = threadRepository.claimIfUnassigned(threadId, userId, Instant.now(), userId);
        if (updated > 0) {
            threadRepository.findById(threadId).ifPresent(t -> decrementUnassigned(t.getMailboxId()));
        }
    }

    private MailThread requireThread(UUID organizationId, UUID threadId) {
        return threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(threadId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Thread"));
    }

    private void decrementUnassigned(UUID mailboxId) {
        mailboxRepository.findById(mailboxId).ifPresent(mb -> {
            mb.setUnassignedCount(Math.max(0, mb.getUnassignedCount() - 1));
            mailboxRepository.save(mb);
        });
    }

    private void incrementUnassigned(UUID mailboxId) {
        mailboxRepository.findById(mailboxId).ifPresent(mb -> {
            mb.setUnassignedCount(mb.getUnassignedCount() + 1);
            mailboxRepository.save(mb);
        });
    }

    private void appendEvent(MailThread thread, MailEnums.ThreadEventType type, String toValue) {
        MailThreadEvent event = new MailThreadEvent();
        event.setOrganizationId(thread.getOrganizationId());
        event.setThreadId(thread.getId());
        event.setEventType(type);
        event.setToValue(toValue);
        eventRepository.save(event);
    }
}
