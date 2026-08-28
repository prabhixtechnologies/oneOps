package com.prabhix.platform.chat.dto;

import com.prabhix.platform.chat.domain.ChatEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    /**
     * Name and email are optional here rather than {@code @NotBlank}, because whether they are
     * required is per-organization: {@code ChatSettings.preChatEnabled} decides. Enforcing it in
     * the DTO would make that setting dead, and switching the pre-chat form off in the console
     * would still leave visitors unable to start a conversation.
     */
    public record PreChatRequest(
            @Size(max = 160) String name,
            @Email @Size(max = 320) String email,
            @Size(max = 500) String subject,
            String visitorKey) {
    }

    @Schema(description = "Start or resume a visitor conversation.")
    public record StartConversationResponse(
            UUID conversationId,
            String conversationToken,
            ChatEnums.ConversationStatus status,
            boolean agentsAvailable) {
    }

    /**
     * {@code internal} marks an agent-only note. It lives in the body rather than only as a
     * query parameter because unknown JSON properties are ignored: a client that sent the
     * flag here while the server read it from the query string would silently publish an
     * internal note to the customer. The visitor endpoint ignores the flag outright.
     */
    public record SendMessageRequest(
            @NotBlank @Size(max = 10000) String body,
            UUID fileId,
            Boolean internal) {

        public boolean isInternal() {
            return Boolean.TRUE.equals(internal);
        }
    }

    public record ConversationSummary(
            UUID id,
            ChatEnums.ConversationStatus status,
            ChatEnums.Priority priority,
            String subject,
            String visitorName,
            String visitorEmail,
            UUID assignedAgentId,
            List<String> tags,
            int unreadAgentCount,
            Instant lastMessageAt,
            String lastMessagePreview,
            UUID visitorId) {
    }

    public record MessageView(
            UUID id,
            ChatEnums.SenderType senderType,
            UUID senderUserId,
            String body,
            UUID fileId,
            Instant occurredAt) {
    }

    public record ConversationDetail(ConversationSummary conversation, List<MessageView> messages) {
    }

    /**
     * Named explicitly because {@code ThreadDtos.AssignRequest} shares the simple name, and
     * springdoc keys schemas by simple name: a collision silently publishes one shape under
     * both paths, which misleads anything generated from the spec.
     */
    @Schema(name = "ChatAssignRequest")
    public record AssignRequest(@NotNull UUID agentId) {
    }

    public record UpdateConversationRequest(
            ChatEnums.ConversationStatus status,
            ChatEnums.Priority priority,
            List<String> tags) {
    }

    public record InboxCounts(long unassigned, long mineUnread) {
    }

    public record CannedReplyView(UUID id, String shortcut, String title, String body) {
    }

    public record CannedReplyRequest(
            @Size(max = 60) String shortcut,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String body) {
    }

    public record SettingsView(
            ChatEnums.Availability availability,
            String awayMessage,
            Map<String, Object> businessHours,
            boolean preChatEnabled,
            UUID offlineMailboxId,
            boolean autoAssignEnabled,
            int maxConcurrentConversations) {
    }

    public record SettingsUpdateRequest(
            ChatEnums.Availability availability,
            String awayMessage,
            Map<String, Object> businessHours,
            Boolean preChatEnabled,
            UUID offlineMailboxId,
            Boolean autoAssignEnabled,
            Integer maxConcurrentConversations) {
    }

    public record TypingEvent(UUID conversationId, String side) {
    }

    public record UploadAck(UUID fileId) {
    }

    public record StartWithVisitorRequest(
            @NotBlank @Size(max = 10000) String message,
            @Size(max = 500) String subject) {
    }

    public record StartWithVisitorResponse(
            UUID conversationId,
            String conversationToken,
            ChatDtos.MessageView openingMessage) {
    }
}
