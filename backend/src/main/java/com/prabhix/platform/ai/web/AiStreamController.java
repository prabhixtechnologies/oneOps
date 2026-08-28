package com.prabhix.platform.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.event.AiStreamEvent;
import com.prabhix.platform.ai.service.AiOrchestrator;
import com.prabhix.platform.ai.service.ChatAiService;
import com.prabhix.platform.ai.service.MailAiService;
import com.prabhix.platform.common.realtime.RealtimeChannelRegistry;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiStreamController {

    private final AiStreamHub hub;
    private final AiOrchestrator orchestrator;
    private final MailAiService mailAiService;
    private final ChatAiService chatAiService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.AI_USE)
    public SseEmitter stream(@CurrentUser PrabhixPrincipal principal) {
        return hub.subscribeUser(principal.requireOrganizationId(), principal.userId());
    }

    @GetMapping(value = "/mail/threads/{threadId}/reply/suggest/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.AI_USE)
    public SseEmitter streamMailReply(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID threadId) {
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

    @GetMapping(value = "/chat/conversations/{conversationId}/reply/suggest/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.AI_USE)
    public SseEmitter streamChatReply(@CurrentUser PrabhixPrincipal principal,
                                      @PathVariable UUID conversationId) {
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

    @Component
    @RequiredArgsConstructor
    static class AiStreamHub {

        private static final String CHANNEL_PREFIX = "ai:stream:";

        private final StringRedisTemplate redis;
        private final ObjectMapper objectMapper;
        private final RealtimeChannelRegistry registry;

        SseEmitter subscribeUser(UUID organizationId, UUID userId) {
            return registry.subscribe(CHANNEL_PREFIX + organizationId + ":" + userId);
        }

        void publish(UUID organizationId, UUID userId, AiStreamEvent event) {
            onAiStreamEvent(event);
        }

        @EventListener
        void onAiStreamEvent(AiStreamEvent event) {
            if (event.organizationId() == null || event.userId() == null) {
                return;
            }
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", event.type(),
                        "conversationId", event.conversationId(),
                        "threadId", event.threadId(),
                        "payload", event.payload()));
                String channel = CHANNEL_PREFIX + event.organizationId() + ":" + event.userId();
                redis.convertAndSend(channel, json);
                registry.fanOutLocal(channel, json);
            } catch (Exception ex) {
                log.debug("Failed to fan-out AI stream event: {}", ex.getMessage());
            }
        }
    }
}
