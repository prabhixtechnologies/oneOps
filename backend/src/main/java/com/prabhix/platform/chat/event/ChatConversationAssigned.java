package com.prabhix.platform.chat.event;

import com.prabhix.platform.common.event.PlatformEvent;

import java.util.UUID;

public record ChatConversationAssigned(
        UUID organizationId,
        UUID conversationId,
        UUID agentId,
        UUID assignedByUserId) implements PlatformEvent {
}
