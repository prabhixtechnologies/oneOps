package com.prabhix.platform.mail.event;

import com.prabhix.platform.common.event.PlatformEvent;

import java.util.UUID;

/** Published after a message is ingested, for SSE fan-out. */
public record MailMessageReceivedEvent(
        UUID organizationId,
        UUID mailboxId,
        UUID threadId,
        UUID messageId) implements PlatformEvent {
}
