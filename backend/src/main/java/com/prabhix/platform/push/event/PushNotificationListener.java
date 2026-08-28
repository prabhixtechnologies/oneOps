package com.prabhix.platform.push.event;

import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatMessage;
import com.prabhix.platform.chat.event.ChatConversationAssigned;
import com.prabhix.platform.chat.event.ChatMessageReceived;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.push.domain.PushToken;
import com.prabhix.platform.push.repository.PushTokenRepository;
import com.prabhix.platform.push.service.PushDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PushNotificationListener {

    private final PushTokenRepository tokenRepository;
    private final PushDispatcher dispatcher;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageReceived(ChatMessageReceived event) {
        if (!event.fromVisitor()) {
            return;
        }
        ChatConversation conversation = conversationRepository.findById(event.conversationId())
                .orElse(null);
        if (conversation == null || conversation.getAssignedAgentId() == null) {
            return;
        }
        ChatMessage message = messageRepository.findById(event.messageId()).orElse(null);
        String body = message == null ? "New message" : truncate(message.getBody());
        notifyAgent(
                event.organizationId(),
                conversation.getAssignedAgentId(),
                "chat.message",
                event.conversationId(),
                null,
                "New chat message",
                body,
                "chat-msg-" + event.messageId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversationAssigned(ChatConversationAssigned event) {
        notifyAgent(
                event.organizationId(),
                event.agentId(),
                "chat.assigned",
                event.conversationId(),
                null,
                "Chat assigned to you",
                "A conversation was assigned to you",
                "chat-assign-" + event.conversationId() + "-" + event.agentId());
    }

    private void notifyAgent(UUID organizationId, UUID agentId, String type, UUID conversationId,
                             UUID threadId, String title, String body, String dedupeKey) {
        List<PushToken> tokens = tokenRepository.findByOrganizationIdAndUserIdAndEnabledTrueAndDeletedAtIsNull(
                organizationId, agentId);
        if (tokens.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("organizationId", organizationId.toString());
        if (conversationId != null) {
            payload.put("conversationId", conversationId.toString());
        }
        if (threadId != null) {
            payload.put("threadId", threadId.toString());
        }
        payload.put("title", title);
        payload.put("body", body);

        for (PushToken token : tokens) {
            dispatcher.enqueue(token, type, payload, dedupeKey + ":" + token.getId());
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "New message";
        }
        return body.length() > 120 ? body.substring(0, 117) + "..." : body;
    }
}
