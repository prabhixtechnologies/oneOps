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

    /**
     * A tag as it appears on a thread: enough to draw a chip, and nothing more.
     *
     * <p>Deliberately not the full tag record. Usage counts and descriptions belong to tag
     * administration, and repeating them on every thread in a queue of fifty would be payload for
     * nothing.
     */
    public record TagRef(UUID id, String slug, String name, String colour) {
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
            Instant slaBreachedAt,
            Instant firstResponseAt,
            Instant resolvedAt,
            // Present so a client can render tag chips at all. Without it, tags were writable through
            // POST /threads/{id}/tags and then invisible in every response that came back, so the only
            // way to know a thread's tags was to replay its event log.
            List<TagRef> tags) {
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
            List<UUID> attachmentIds,
            /**
             * The canned reply this body started from, if any. Optional, and only used to count usage.
             *
             * <p>The body is still sent verbatim from {@code bodyHtml}, because the agent has usually
             * edited it. Resolving the canned reply server-side would discard those edits, and
             * counting a use without sending the text is the honest division: {@code usage_count} was
             * displayed in the admin list and never incremented, so every canned reply read as unused
             * no matter how often it was sent.
             */
            UUID cannedReplyId) {

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
