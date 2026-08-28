package com.prabhix.platform.mail.dto;

import com.prabhix.platform.mail.domain.MailEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ThreadDtos {

    private ThreadDtos() {
    }

    public record ThreadListQuery(
            UUID mailboxId,
            MailEnums.ThreadStatus status,
            MailEnums.Priority priority,
            UUID assigneeUserId,
            UUID assigneeTeamId,
            UUID tagId,
            boolean unreadOnly,
            boolean hasAttachment,
            String q,
            String cursor,
            Integer limit) {
    }

    public record ThreadSummary(
            UUID id,
            UUID mailboxId,
            String referenceKey,
            String subject,
            MailEnums.ThreadStatus status,
            MailEnums.Priority priority,
            UUID assigneeUserId,
            UUID assigneeTeamId,
            String customerEmail,
            String snippet,
            int messageCount,
            int unreadCount,
            boolean hasAttachments,
            Instant lastMessageAt,
            MailEnums.MessageDirection lastMessageDirection,
            Instant slaDueAt,
            Instant slaBreachedAt) {
    }

    public record MessageSummary(
            UUID id,
            MailEnums.MessageDirection direction,
            String fromAddress,
            String fromName,
            String subject,
            String snippet,
            String bodyText,
            String bodyHtml,
            MailEnums.DeliveryStatus deliveryStatus,
            Instant occurredAt,
            int attachmentCount) {
    }

    public record NoteSummary(UUID id, UUID authorUserId, String bodyHtml, Instant createdAt) {
    }

    public record EventSummary(
            MailEnums.ThreadEventType eventType,
            UUID actorUserId,
            String actorLabel,
            String fromValue,
            String toValue,
            Instant createdAt) {
    }

    public record ThreadDetail(
            ThreadSummary thread,
            List<MessageSummary> messages,
            List<NoteSummary> notes,
            List<EventSummary> events) {
    }

    public record UpdateThreadRequest(MailEnums.ThreadStatus status, MailEnums.Priority priority) {
    }

    public record BulkUpdateRequest(
            @NotEmpty List<UUID> threadIds,
            MailEnums.ThreadStatus status,
            MailEnums.Priority priority) {
    }

    public record ReplyRequest(
            MailEnums.ReplyMode replyMode,
            List<String> to,
            List<String> cc,
            String subject,
            @NotBlank String bodyHtml,
            List<UUID> attachmentIds) {

        public ReplyRequest {
            if (replyMode == null) {
                replyMode = MailEnums.ReplyMode.REPLY;
            }
        }
    }

    public record AssignRequest(UUID userId, UUID teamId) {
    }

    public record CreateNoteRequest(@NotBlank String bodyHtml) {
    }
}
