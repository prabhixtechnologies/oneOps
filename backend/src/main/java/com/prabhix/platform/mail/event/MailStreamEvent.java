package com.prabhix.platform.mail.event;

import com.prabhix.platform.common.event.PlatformEvent;

import java.util.UUID;

/** Generic mail SSE payload wrapper. */
public record MailStreamEvent(
        String type,
        UUID organizationId,
        Object payload) implements PlatformEvent {
}
