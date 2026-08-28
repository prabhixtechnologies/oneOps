package com.prabhix.platform.chat.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.event.MailRequested;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.security.tenant.TenantContext;
import com.prabhix.platform.visitor.domain.Visitor;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import com.prabhix.platform.visitor.service.VisitorPresenceService;
import com.prabhix.platform.visitor.service.VisitorStitchService;
import com.prabhix.platform.visitor.dto.VisitorDtos;
import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.domain.ChatMessage;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.event.ChatConversationAssigned;
import com.prabhix.platform.chat.event.ChatVisitorStreamEvent;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import com.prabhix.platform.chat.util.ChatCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private final OrganizationRepository organizationRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSettingsRepository settingsRepository;
    private final VisitorStitchService visitorStitchService;
    private final VisitorPresenceService presenceService;
    private final VisitorRepository visitorRepository;
    private final ObjectProvider<ChatMessageService> messageService;
    private final ChatTokenService tokenService;
    private final EntitlementGate entitlements;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    public Organization resolveOrg(String orgSlug) {
        return organizationRepository.findBySlug(orgSlug)
                .orElseThrow(() -> ApiException.notFound("Organization"));
    }

    @Transactional
    public ChatDtos.StartConversationResponse startPublic(String orgSlug, ChatDtos.PreChatRequest request) {
        Organization org = resolveOrg(orgSlug);
        return TenantContext.callAs(org.getId(), () -> {
            entitlements.requireFeature(org.getId(), "chat");
            ChatSettings settings = settingsOrDefault(org.getId());
            boolean available = settings.getAvailability() == ChatEnums.Availability.ONLINE;

            String name = trimToNull(request.name());
            String email = trimToNull(request.email());
            // The pre-chat form is what makes these mandatory. With it switched off a visitor can
            // open a conversation anonymously, and we learn who they are later if they tell us.
            if (settings.isPreChatEnabled() && (name == null || email == null)) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                        "Please provide your name and email address to start a chat");
            }

            Visitor visitor = null;
            if (request.visitorKey() != null && !request.visitorKey().isBlank()) {
                visitor = visitorStitchService.identify(org.getId(), request.visitorKey(),
                        email, name, null);
            }

            ChatConversation conversation = new ChatConversation();
            conversation.setOrganizationId(org.getId());
            conversation.setStatus(ChatEnums.ConversationStatus.OPEN);
            conversation.setVisitorName(name);
            conversation.setVisitorEmail(email == null ? null : email.toLowerCase());
            conversation.setSubject(request.subject());
            if (visitor != null) {
                conversation.setVisitorId(visitor.getId());
            }
            conversation = conversationRepository.save(conversation);

            String token = tokenService.issue(org.getId(), conversation.getId(),
                    conversation.getVisitorId());
            events.publishEvent(AuditRequested.of(org.getId(), null,
                    "chat.conversation.started", "chat_conversation", conversation.getId()));
            return new ChatDtos.StartConversationResponse(
                    conversation.getId(), token, conversation.getStatus(), available);
        });
    }

    @Transactional(readOnly = true)
    public CursorPage<ChatDtos.ConversationSummary> list(PrabhixPrincipal principal, String queue,
                                                         String status, String cursor, Integer limit) {
        UUID orgId = principal.requireOrganizationId();
        boolean readAll = principal.has(Permission.CHAT_READ_ALL);
        if ("all".equals(queue) && !readAll) {
            throw ApiException.forbidden("You may not view all conversations");
        }
        if (queue == null || queue.isBlank()) {
            queue = readAll ? "all" : "mine";
        }
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<ChatConversation> fetched = conversationRepository.listWithCursor(
                orgId, queue, principal.userId(), status,
                decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toSummary, ChatCursor::encodeConversation);
    }

    @Transactional(readOnly = true)
    public ChatDtos.ConversationDetail get(PrabhixPrincipal principal, UUID conversationId) {
        ChatConversation conversation = loadVisible(principal, conversationId);
        List<ChatMessage> messages = messageRepository
                .findByConversationIdAndDeletedAtIsNullOrderByOccurredAtAsc(conversationId);
        return new ChatDtos.ConversationDetail(toSummary(conversation),
                messages.stream().map(this::toMessage).toList());
    }

    @Transactional
    public ChatDtos.ConversationSummary assign(PrabhixPrincipal principal, UUID conversationId,
                                               ChatDtos.AssignRequest request) {
        ChatConversation conversation = loadVisible(principal, conversationId);
        conversation.setAssignedAgentId(request.agentId());
        conversation = conversationRepository.save(conversation);
        events.publishEvent(new ChatConversationAssigned(
                conversation.getOrganizationId(), conversationId, request.agentId(), principal.userId()));
        events.publishEvent(AuditRequested.changed(conversation.getOrganizationId(), principal.userId(),
                "chat.conversation.assigned", "chat_conversation", conversationId,
                Map.of("agentId", request.agentId())));
        return toSummary(conversation);
    }

    @Transactional
    public ChatDtos.ConversationSummary update(PrabhixPrincipal principal, UUID conversationId,
                                               ChatDtos.UpdateConversationRequest request) {
        ChatConversation conversation = loadVisible(principal, conversationId);
        if (request.status() != null) {
            conversation.setStatus(request.status());
            if (request.status() == ChatEnums.ConversationStatus.CLOSED) {
                conversation.setClosedAt(Instant.now());
                sendTranscript(conversation);
            }
        }
        if (request.priority() != null) {
            conversation.setPriority(request.priority());
        }
        if (request.tags() != null) {
            conversation.setTags(request.tags());
        }
        return toSummary(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public ChatDtos.InboxCounts counts(PrabhixPrincipal principal) {
        UUID orgId = principal.requireOrganizationId();
        return new ChatDtos.InboxCounts(
                conversationRepository.countUnassigned(orgId),
                conversationRepository.countUnreadForAgent(orgId, principal.userId()));
    }

    @Transactional
    public ChatDtos.StartWithVisitorResponse startWithLiveVisitor(PrabhixPrincipal principal,
                                                                  UUID visitorId,
                                                                  ChatDtos.StartWithVisitorRequest request) {
        UUID orgId = principal.requireOrganizationId();
        entitlements.requireFeature(orgId, "chat");

        boolean live = presenceService.listLive(orgId).stream()
                .anyMatch(v -> v.visitorId().equals(visitorId));
        if (!live) {
            throw ApiException.of(ErrorCode.INVALID_STATE,
                    "That visitor is no longer on the site. Try again when they return.");
        }

        var visitor = visitorRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(visitorId, orgId)
                .orElseThrow(() -> ApiException.of(ErrorCode.VISITOR_NOT_FOUND, "Visitor was not found"));

        VisitorDtos.LiveVisitor liveMeta = presenceService.listLive(orgId).stream()
                .filter(v -> v.visitorId().equals(visitorId))
                .findFirst()
                .orElse(null);

        ChatConversation conversation = new ChatConversation();
        conversation.setOrganizationId(orgId);
        conversation.setStatus(ChatEnums.ConversationStatus.OPEN);
        conversation.setVisitorId(visitorId);
        conversation.setVisitorName(liveMeta != null && liveMeta.displayName() != null
                ? liveMeta.displayName()
                : visitor.getDisplayName());
        conversation.setVisitorEmail(liveMeta != null && liveMeta.email() != null
                ? liveMeta.email().toLowerCase()
                : visitor.getEmail());
        conversation.setSubject(request.subject());
        conversation.setAssignedAgentId(principal.userId());
        conversation = conversationRepository.save(conversation);

        String token = tokenService.issue(orgId, conversation.getId(), visitorId);

        ChatDtos.MessageView opening = messageService.getObject().sendAgent(
                principal, conversation.getId(),
                new ChatDtos.SendMessageRequest(request.message(), null, false),
                false, null);

        events.publishEvent(new ChatConversationAssigned(
                orgId, conversation.getId(), principal.userId(), principal.userId()));
        events.publishEvent(new ChatVisitorStreamEvent(
                orgId, visitorId, "proactive_chat",
                Map.of(
                        "conversationId", conversation.getId(),
                        "conversationToken", token,
                        "message", opening)));
        events.publishEvent(AuditRequested.of(orgId, principal.userId(),
                "chat.conversation.started.proactive", "chat_conversation", conversation.getId()));

        return new ChatDtos.StartWithVisitorResponse(conversation.getId(), token, opening);
    }

    private void sendTranscript(ChatConversation conversation) {
        if (conversation.getVisitorEmail() == null || conversation.getVisitorEmail().isBlank()) {
            return;
        }
        List<ChatMessage> messages = messageRepository
                .findByConversationIdAndDeletedAtIsNullOrderByOccurredAtAsc(conversation.getId());
        StringBuilder html = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message.getSenderType() == ChatEnums.SenderType.NOTE) {
                continue;
            }
            html.append("<p><strong>")
                    .append(message.getSenderType().name())
                    .append(":</strong> ")
                    .append(message.getBody())
                    .append("</p>");
        }
        events.publishEvent(MailRequested.forOrganization(
                conversation.getOrganizationId(),
                conversation.getVisitorEmail(),
                "chat.transcript",
                Map.of("organizationName", "Support", "transcriptHtml", html.toString()),
                "chat-transcript-" + conversation.getId()));
    }

    private ChatConversation loadVisible(PrabhixPrincipal principal, UUID conversationId) {
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
        return conversation;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ChatSettings settingsOrDefault(UUID orgId) {
        return settingsRepository.findByOrganizationId(orgId).orElseGet(() -> {
            ChatSettings settings = new ChatSettings();
            settings.setOrganizationId(orgId);
            return settingsRepository.save(settings);
        });
    }

    private ChatDtos.ConversationSummary toSummary(ChatConversation conversation) {
        return new ChatDtos.ConversationSummary(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getPriority(),
                conversation.getSubject(),
                conversation.getVisitorName(),
                conversation.getVisitorEmail(),
                conversation.getAssignedAgentId(),
                conversation.getTags(),
                conversation.getUnreadAgentCount(),
                conversation.getLastMessageAt(),
                conversation.getLastMessagePreview(),
                conversation.getVisitorId());
    }

    private ChatDtos.MessageView toMessage(ChatMessage message) {
        return new ChatDtos.MessageView(
                message.getId(),
                message.getSenderType(),
                message.getSenderUserId(),
                message.getBody(),
                message.getFileId(),
                message.getOccurredAt());
    }
}
