package com.prabhix.platform.mail.web;

import com.prabhix.platform.ai.event.AiStreamEvent;
import com.prabhix.platform.ai.service.AiOrchestrator;
import com.prabhix.platform.ai.web.AiStreamHub;
import com.prabhix.platform.mail.ai.MailAiService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Streams a suggested reply for a mail thread.
 *
 * <p>The route is unchanged — still under {@code /api/v1/ai} — because it is an AI endpoint from the
 * client's point of view and the console calls it by that URL. What changed is which module serves
 * it: it was in the AI module, which had to compile against mail to build the prompt, and mail is
 * the module being extracted.
 */
@RestController
@RequestMapping("/api/v1/ai/mail")
@RequiredArgsConstructor
public class MailAiStreamController {

    private final AiStreamHub hub;
    private final AiOrchestrator orchestrator;
    private final MailAiService mailAiService;

    @GetMapping(value = "/threads/{threadId}/reply/suggest/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.AI_USE)
    public SseEmitter streamMailReply(@CurrentUser PrabhixPrincipal principal,
                                      @PathVariable UUID threadId) {
        UUID orgId = principal.requireOrganizationId();
        SseEmitter emitter = hub.subscribeUser(orgId, principal.userId());
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ai-mail-stream");
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            try {
                orchestrator.stream(new AiOrchestrator.AiRequest(
                        orgId, principal.userId(), "mail", "mail.reply_suggest",
                        mailAiService.streamVariables(principal, threadId),
                        null, null, "mail_thread", threadId), chunk -> {
                    hub.publish(orgId, principal.userId(), new AiStreamEvent(
                            orgId, principal.userId(), "ai.delta", null, threadId,
                            Map.of("delta", chunk.delta(), "finished", chunk.finished())));
                });
            } catch (Exception ex) {
                hub.publish(orgId, principal.userId(), new AiStreamEvent(
                        orgId, principal.userId(), "ai.error", null, threadId,
                        Map.of("message", ex.getMessage())));
            }
        });
        return emitter;
    }
}
