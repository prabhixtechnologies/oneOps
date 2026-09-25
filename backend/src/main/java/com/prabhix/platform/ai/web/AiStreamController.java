package com.prabhix.platform.ai.web;

import com.prabhix.platform.ai.event.AiStreamEvent;
import com.prabhix.platform.ai.service.AiOrchestrator;
import com.prabhix.platform.ai.service.ChatAiService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * The generic AI stream, plus the chat suggestion stream.
 *
 * <p>The mail thread equivalent lives in the mail module, at the same URL prefix. Both reach the
 * same {@link AiStreamHub}; what moved is only which module has to be compiled to serve the route.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oneops/ai")
@RequiredArgsConstructor
public class AiStreamController {

    private final AiStreamHub hub;
    private final AiOrchestrator orchestrator;
    private final ChatAiService chatAiService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.AI_USE)
    public SseEmitter stream(@CurrentUser PrabhixPrincipal principal) {
        return hub.subscribeUser(principal.requireOrganizationId(), principal.userId());
    }

    @GetMapping(value = "/chat/conversations/reply/suggest/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.AI_USE)
    public SseEmitter streamChatReply(@CurrentUser PrabhixPrincipal principal,
                                      @RequestParam UUID conversationId) {
        UUID orgId = principal.requireOrganizationId();
        SseEmitter emitter = hub.subscribeUser(orgId, principal.userId());
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ai-chat-stream");
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            try {
                orchestrator.stream(new AiOrchestrator.AiRequest(
                        orgId, principal.userId(), "chat", "chat.reply_suggest",
                        chatAiService.streamVariables(principal, conversationId),
                        null, null, "chat_conversation", conversationId), chunk -> {
                    hub.publish(orgId, principal.userId(), new AiStreamEvent(
                            orgId, principal.userId(), "ai.delta", conversationId, null,
                            Map.of("delta", chunk.delta(), "finished", chunk.finished())));
                });
            } catch (Exception ex) {
                hub.publish(orgId, principal.userId(), new AiStreamEvent(
                        orgId, principal.userId(), "ai.error", conversationId, null,
                        Map.of("message", ex.getMessage())));
            }
        });
        return emitter;
    }
}
