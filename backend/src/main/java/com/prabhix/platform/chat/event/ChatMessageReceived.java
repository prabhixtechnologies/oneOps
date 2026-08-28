package com.prabhix.platform.chat.event;

import com.prabhix.platform.common.event.PlatformEvent;

import java.util.UUID;

public record ChatMessageReceived(
        UUID organizationId,
        UUID conversationId,
        UUID messageId,
        boolean fromVisitor) implements PlatformEvent {
}
