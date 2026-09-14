package com.prabhix.platform.chat.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.common.mail.MailRequest;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.service.AttachmentValidationService;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.security.tenant.TenantContext;
import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.domain.ChatMessage;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.event.ChatMessageReceived;
import com.prabhix.platform.chat.event.ChatStreamEvent;
import com.prabhix.platform.chat.event.ChatVisitorStreamEvent;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import com.prabhix.platform.chat.util.ChatCursor;
import com.prabhix.platform.common.spi.MailboxDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSettingsRepository settingsRepository;
    private final ChatTokenService tokenService;
    private final ChatAssignmentRouter assignmentRouter;
    private final AttachmentValidationService attachmentValidationService;
    private final MailboxDirectory mailboxes;
    private final EntitlementGate entitlements;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;
    private final MailClient mail;
    private final ChatMessageIdempotencyService idempotencyService;
    private final StructuredEventLogger eventLogger;

    @Transactional
    public ChatDtos.MessageView sendVisitor(String orgSlug, UUID conversationId, String token,
                                            ChatDtos.SendMessageRequest request, String idempotencyKey) {
        ChatTokenService.ConversationToken parsed = tokenService.parse(token);
        tokenService.assertConversation(parsed, conversationId);
        return TenantContext.callAs(parsed.organizationId(), () -> {
            entitlements.requireFeature(parsed.organizationId(), "chat");
            ChatConversation conversation = conversationRepository
                    .findByIdAndOrganizationIdAndDeletedAtIsNull(conversationId, parsed.organizationId())
                    .orElseThrow(() -> ApiException.notFound("Conversation"));
            return idempotencyService.execute(
                    parsed.organizationId(), conversationId, idempotencyKey,
                    () -> persistMessage(conversation, ChatEnums.SenderType.VISITOR, null, request, true));
        });
    }

    @Transactional
    public ChatDtos.MessageView sendAgent(PrabhixPrincipal principal, UUID conversationId,
                                          ChatDtos.SendMessageRequest request, boolean internalNote,
                                          String idempotencyKey) {
        UUID orgId = principal.requireOrganizationId();
        entitlements.requireFeature(orgId, "chat");
        ChatConversation conversation = conversationRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(conversationId, orgId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
        ChatEnums.SenderType type = internalNote ? ChatEnums.SenderType.NOTE : ChatEnums.SenderType.AGENT;
        return idempotencyService.execute(
                orgId, conversationId, idempotencyKey,
                () -> persistMessage(conversation, type, principal.userId(), request, false));
    }

    @Transactional(readOnly = true)
    public CursorPage<ChatDtos.MessageView> listVisitor(String token, UUID conversationId,
                                                        String cursor, Integer limit) {
        ChatTokenService.ConversationToken parsed = tokenService.parse(token);
        tokenService.assertConversation(parsed, conversationId);
        return TenantContext.callAs(parsed.organizationId(), () -> listMessages(conversationId, cursor, limit, true));
    }

    @Transactional(readOnly = true)
    public CursorPage<ChatDtos.MessageView> listAgent(PrabhixPrincipal principal, UUID conversationId,
                                                      String cursor, Integer limit) {
        assertAgentVisible(principal, conversationId);
        return listMessages(conversationId, cursor, limit, false);
    }

    private void assertAgentVisible(PrabhixPrincipal principal, UUID conversationId) {
        UUID orgId = principal.requireOrganizationId();
        ChatConversation conversation = conversationRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(conversationId, orgId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
        if (!principal.has(Permission.CHAT_READ_ALL)) {
            UUID agentId = conversation.getAssignedAgentId();
            if (agentId == null || !agentId.equals(principal.userId())) {
                throw ApiException.forbidden("Conversation is not assigned to you");
            }
        }
    }

    private CursorPage<ChatDtos.MessageView> listMessages(UUID conversationId, String cursor,
                                                          Integer limit, boolean visitorView) {
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<ChatMessage> fetched = messageRepository.listWithCursor(
                conversationId, !visitorView, decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toView, ChatCursor::encodeMessage);
    }

    private ChatDtos.MessageView persistMessage(ChatConversation conversation,
                                                ChatEnums.SenderType senderType,
                                                UUID senderUserId,
                                                ChatDtos.SendMessageRequest request,
                                                boolean fromVisitor) {
        ChatSettings settings = settingsRepository.findByOrganizationId(conversation.getOrganizationId())
                .orElse(null);
        if (request.fileId() != null) {
            attachmentValidationService.requireCleanAttachments(
                    conversation.getOrganizationId(), List.of(request.fileId()));
        }

        ChatMessage message = new ChatMessage();
        message.setOrganizationId(conversation.getOrganizationId());
        message.setConversationId(conversation.getId());
        message.setSenderType(senderType);
        message.setSenderUserId(senderUserId);
        message.setBody(request.body());
        message.setFileId(request.fileId());
        message = messageRepository.save(message);

        conversation.setLastMessageAt(message.getOccurredAt());
        conversation.setLastMessagePreview(truncate(request.body()));
        if (fromVisitor) {
            conversation.setUnreadAgentCount(conversation.getUnreadAgentCount() + 1);
        } else if (senderType == ChatEnums.SenderType.AGENT) {
            conversation.setUnreadVisitorCount(conversation.getUnreadVisitorCount() + 1);
        }
        conversationRepository.save(conversation);

        events.publishEvent(new ChatMessageReceived(
                conversation.getOrganizationId(), conversation.getId(), message.getId(), fromVisitor));
        events.publishEvent(new ChatStreamEvent(
                conversation.getOrganizationId(), conversation.getId(), "message",
                Map.of("messageId", message.getId(), "senderType", senderType.name())));
        events.publishEvent(AuditRequested.of(conversation.getOrganizationId(), senderUserId,
                "chat.message.sent", "chat_message", message.getId()));
        eventLogger.log(
                fromVisitor ? LogEventCode.CHAT_MESSAGE_RECEIVED : LogEventCode.CHAT_MESSAGE_SENT,
                Map.of("messageId", message.getId(), "conversationId", conversation.getId()));

        if (fromVisitor && conversation.getAssignedAgentId() == null) {
            assignmentRouter.assignIfNeeded(conversation);
            conversation = conversationRepository.findById(conversation.getId()).orElse(conversation);
        }

        // Once, and after the assignment attempt above, because whether anyone will see this message
        // is not known until then. There were two of these: one before the save for OFFLINE, one here
        // for "unassigned and not ONLINE". An offline organization is also an unassigned one, so the
        // common case ran both and mailed support twice. The old test asked only whether some mail
        // event had gone past, so it never saw the second.
        if (fromVisitor && settings != null && isUnattended(settings, conversation)) {
            routeOffline(conversation, message);
        }

        return toView(message);
    }

    @Transactional
    public ChatDtos.MessageView sendAiReply(ChatConversation conversation, String body) {
        ChatMessage message = new ChatMessage();
        message.setOrganizationId(conversation.getOrganizationId());
        message.setConversationId(conversation.getId());
        message.setSenderType(ChatEnums.SenderType.AI);
        message.setBody(body);
        message = messageRepository.save(message);

        conversation.setLastMessageAt(message.getOccurredAt());
        conversation.setLastMessagePreview(truncate(body));
        conversation.setUnreadVisitorCount(conversation.getUnreadVisitorCount() + 1);
        conversationRepository.save(conversation);

        events.publishEvent(new ChatStreamEvent(
                conversation.getOrganizationId(), conversation.getId(), "message",
                Map.of(
                        "messageId", message.getId(),
                        "senderType", ChatEnums.SenderType.AI.name(),
                        "aiReply", true)));
        if (conversation.getVisitorId() != null) {
            events.publishEvent(new ChatVisitorStreamEvent(
                    conversation.getOrganizationId(), conversation.getVisitorId(), "ai_message",
                    Map.of(
                            "conversationId", conversation.getId(),
                            "messageId", message.getId(),
                            "body", body,
                            "senderType", ChatEnums.SenderType.AI.name())));
        }
        return toView(message);
    }

    /** Nobody is going to read this in the product: the organization is away, or no agent has it. */
    private boolean isUnattended(ChatSettings settings, ChatConversation conversation) {
        return switch (settings.getAvailability()) {
            case OFFLINE -> true;
            case AWAY -> conversation.getAssignedAgentId() == null;
            case ONLINE -> false;
        };
    }

    private void routeOffline(ChatConversation conversation, ChatMessage message) {
        ChatSettings settings = settingsRepository.findByOrganizationId(conversation.getOrganizationId())
                .orElse(null);
        if (settings == null || settings.getOfflineMailboxId() == null) {
            return;
        }
        mailboxes.addressOf(conversation.getOrganizationId(), settings.getOfflineMailboxId())
                .ifPresent(address ->
                mail.send(MailRequest.forOrganization(
                        conversation.getOrganizationId(),
                        address,
                        "chat.offline-message",
                        Map.of(
                                "visitorName", conversation.getVisitorName() == null ? "Visitor" : conversation.getVisitorName(),
                                "visitorEmail", conversation.getVisitorEmail() == null ? "" : conversation.getVisitorEmail(),
                                "subject", conversation.getSubject() == null ? "Offline chat" : conversation.getSubject(),
                                "messageBody", message.getBody()),
                        // The message id. Was the conversation id and the current millisecond, which
                        // made every call a distinct key and so deduplicated nothing — the property
                        // worth having is one notification per message, whatever retries it through.
                        "chat-offline-" + message.getId())));
    }

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() > 200 ? body.substring(0, 197) + "..." : body;
    }

    private ChatDtos.MessageView toView(ChatMessage message) {
        return new ChatDtos.MessageView(
                message.getId(),
                message.getSenderType(),
                message.getSenderUserId(),
                message.getBody(),
                message.getFileId(),
                message.getOccurredAt());
    }
}
