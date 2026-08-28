package com.prabhix.platform.chat.event;

import com.prabhix.platform.chat.service.ChatFirstResponderService;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives the AI first responder off the committed visitor message rather than inline.
 *
 * <p>Inline was wrong twice over. It held the visitor's write transaction open for the length of
 * a provider round trip (up to {@code prabhix.ai.request-timeout}), and it put a bean cycle in the
 * graph: chat AI needs the message service to post its reply, so the message service cannot also
 * depend on the responder.
 *
 * <p>{@code @Async} keeps the provider latency off the request thread entirely, so the visitor's
 * message returns at write speed no matter how slow the model is. That means a pooled thread, and
 * a pooled thread cannot be trusted to have inherited the right tenant, so the work is wrapped in
 * an explicit {@link TenantContext#runAs}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatFirstResponderListener {

    private final ChatFirstResponderService firstResponderService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageReceived(ChatMessageReceived event) {
        if (!event.fromVisitor()) {
            return;
        }
        try {
            TenantContext.runAs(event.organizationId(),
                    () -> firstResponderService.maybeReply(event.conversationId(), event.messageId()));
        } catch (Exception ex) {
            log.debug("First responder skipped for conversation {}: {}",
                    event.conversationId(), ex.getMessage());
        }
    }
}
