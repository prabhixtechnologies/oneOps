package com.prabhix.platform.ai.event;

import java.util.Map;
import java.util.UUID;

public record AiStreamEvent(
        UUID organizationId,
        UUID userId,
        String type,
        UUID conversationId,
        UUID threadId,
        Map<String, Object> payload) {
}
