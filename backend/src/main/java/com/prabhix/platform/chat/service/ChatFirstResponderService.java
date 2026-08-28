package com.prabhix.platform.chat.service;

import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.service.ChatAiService;
import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.domain.ChatMessage;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Optional AI greeting when no human is online.
 *
 * <p>Invoked from {@code ChatFirstResponderListener} after the visitor's message has committed,
 * so every failure here is swallowed: the visitor has already been served, and a provider outage
 * or an exhausted quota must never surface to them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFirstResponderService {

    private final ChatAiService chatAiService;
    private final ChatMessageService messageService;
    private final ChatConversationRepository conversationRepository;
    private final ChatSettingsRepository settingsRepository;
    private final ChatMessageRepository messageRepository;

    // The publishing transaction has already committed, so the reads below need one of their own.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void maybeReply(UUID conversationId, UUID messageId) {
        try {
            ChatConversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null || !shouldRespond(conversation)) {
                return;
            }
            ChatMessage visitorMessage = messageRepository.findById(messageId).orElse(null);
            if (visitorMessage == null || visitorMessage.getBody() == null
                    || visitorMessage.getBody().isBlank()) {
                return;
            }

            AiDtos.DraftSuggestion suggestion = chatAiService.firstResponder(
                    conversation.getOrganizationId(), conversation.getId(), visitorMessage.getBody());
            if (!suggestion.available() || suggestion.draft() == null || suggestion.draft().isBlank()) {
                return;
            }
            messageService.sendAiReply(conversation, suggestion.draft());
        } catch (Exception ex) {
            log.debug("First responder skipped for conversation {}: {}", conversationId, ex.getMessage());
        }
    }

    private boolean shouldRespond(ChatConversation conversation) {
        if (conversation.getAssignedAgentId() != null) {
            return false;
        }
        ChatSettings settings = settingsRepository.findByOrganizationId(conversation.getOrganizationId())
                .orElse(null);
        if (settings == null || settings.getAvailability() == ChatEnums.Availability.ONLINE) {
            return false;
        }
        // Only greet the opening message. A running conversation with no agent is the offline
        // routing path's problem, not the greeter's.
        long visitorMessages = messageRepository.countByConversationIdAndSenderTypeAndDeletedAtIsNull(
                conversation.getId(), ChatEnums.SenderType.VISITOR);
        return visitorMessages == 1;
    }
}
