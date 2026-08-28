package com.prabhix.platform.chat.util;

import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatMessage;
import com.prabhix.platform.common.web.Cursor;

import java.time.Instant;

public final class ChatCursor {

    private ChatCursor() {
    }

    public static String encodeConversation(ChatConversation conversation) {
        Instant at = conversation.getLastMessageAt() != null
                ? conversation.getLastMessageAt()
                : conversation.getCreatedAt();
        return Cursor.of(at, conversation.getId()).encode();
    }

    public static String encodeMessage(ChatMessage message) {
        return Cursor.of(message.getOccurredAt(), message.getId()).encode();
    }
}
