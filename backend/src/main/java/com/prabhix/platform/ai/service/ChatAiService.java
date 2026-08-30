package com.prabhix.platform.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.domain.AiOrgSettings;
import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.repository.AiOrgSettingsRepository;
import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.domain.ChatMessage;
import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatAiService {

    private final AiOrchestrator orchestrator;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AiOrgSettingsRepository orgSettingsRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;
    private final com.prabhix.platform.chat.service.ChatMessageService chatMessageService;

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> streamVariables(PrabhixPrincipal principal, UUID conversationId) {
        UUID orgId = principal.requireOrganizationId();
        loadConversation(principal, conversationId);
        return Map.of(
                "orgName", orgName(orgId),
                "conversationHistory", formatHistory(conversationId));
    }

    @Transactional(readOnly = true)
    public AiDtos.DraftSuggestion suggestReply(PrabhixPrincipal principal, UUID conversationId) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        ChatConversation conversation = loadConversation(principal, conversationId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return AiDtos.DraftSuggestion.unavailable(availability);
        }
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "chat", "chat.reply_suggest",
                Map.of(
                        "orgName", orgName(orgId),
                        "conversationHistory", formatHistory(conversationId)),
                null, null, "chat_conversation", conversationId));
        return AiDtos.DraftSuggestion.of(result.text(), result);
    }

    @Transactional(readOnly = true)
    public AiDtos.RewriteResult rewriteMessage(PrabhixPrincipal principal, UUID conversationId,
                                                 AiDtos.RewriteRequest request) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        loadConversation(principal, conversationId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return new AiDtos.RewriteResult(false, "", null, null);
        }
        String action = request.action() != null ? request.action() : "Improve";
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "chat", "chat.message_rewrite",
                Map.of("draft", request.draft(), "action", action),
                null, null, "chat_conversation", conversationId));
        return new AiDtos.RewriteResult(true, result.text(), result.provider().configKey(), result.model());
    }

    @Transactional
    public AiDtos.HandoffSummaryResult createHandoffSummary(PrabhixPrincipal principal, UUID conversationId) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        loadConversation(principal, conversationId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return new AiDtos.HandoffSummaryResult(false, null, "");
        }
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "chat", "chat.handoff_summary",
                Map.of("conversationHistory", formatHistory(conversationId)),
                null, null, "chat_conversation", conversationId));
        ChatDtos.MessageView note = chatMessageService.sendAgent(
                principal, conversationId,
                new ChatDtos.SendMessageRequest(result.text(), null, true),
                true, null);
        return new AiDtos.HandoffSummaryResult(true, note.id(), result.text());
    }

    @Transactional(readOnly = true)
    public AiDtos.SentimentResult analyzeSentiment(PrabhixPrincipal principal, UUID conversationId) {
        assertAiUse(principal);
        UUID orgId = principal.requireOrganizationId();
        loadConversation(principal, conversationId);
        var availability = orchestrator.availability(orgId);
        if (!availability.configured()) {
            return new AiDtos.SentimentResult(false, null, null, null);
        }
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, principal.userId(), "chat", "chat.sentiment",
                Map.of("conversationHistory", formatHistory(conversationId)),
                null, null, "chat_conversation", conversationId));
        return parseSentiment(result.text());
    }

    @Transactional(readOnly = true)
    public AiDtos.DraftSuggestion firstResponder(UUID organizationId, UUID conversationId, String visitorMessage) {
        AiOrgSettings settings = orgSettingsRepository.findByOrganizationId(organizationId).orElse(null);
        if (settings == null || !settings.isFirstResponderEnabled()) {
            return new AiDtos.DraftSuggestion(false, "", null, null, false, false);
        }
        var availability = orchestrator.availability(organizationId);
        if (!availability.configured()) {
            return AiDtos.DraftSuggestion.unavailable(availability);
        }
        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                organizationId, null, "chat", "chat.first_responder",
                Map.of("orgName", orgName(organizationId), "visitorMessage", visitorMessage),
                null, null, "chat_conversation", conversationId));
        return AiDtos.DraftSuggestion.of(result.text(), result);
    }

    private AiDtos.SentimentResult parseSentiment(String text) {
        try {
            String json = text.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode node = objectMapper.readTree(json);
            return new AiDtos.SentimentResult(
                    true,
                    node.path("sentiment").asText(null),
                    node.path("urgency").asText(null),
                    node.path("summary").asText(null));
        } catch (Exception ex) {
            return new AiDtos.SentimentResult(true, "neutral", "medium", text);
        }
    }

    private ChatConversation loadConversation(PrabhixPrincipal principal, UUID conversationId) {
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

    private String formatHistory(UUID conversationId) {
        return messageRepository.findByConversationIdAndDeletedAtIsNullOrderByOccurredAtAsc(conversationId).stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    private String formatMessage(ChatMessage message) {
        return message.getSenderType().name() + ": " + message.getBody();
    }

    private String orgName(UUID orgId) {
        return organizationRepository.findById(orgId).map(o -> o.getName()).orElse("Support");
    }

    private void assertAiUse(PrabhixPrincipal principal) {
        if (!principal.has(Permission.AI_USE)) {
            throw ApiException.forbidden("AI_USE permission required");
        }
    }
}
