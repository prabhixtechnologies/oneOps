package com.prabhix.platform.chat.event;

import com.prabhix.platform.common.event.PlatformEvent;

import java.util.Map;
import java.util.UUID;

public record ChatVisitorStreamEvent(UUID organizationId, UUID visitorId, String type, Map<String, Object> payload)
        implements PlatformEvent {
}
