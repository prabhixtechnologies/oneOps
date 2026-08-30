package com.prabhix.platform.ai.web;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.event.AiStreamEvent;
import com.prabhix.platform.common.realtime.RealtimeChannelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * One SSE channel per user for streamed AI output, whatever produced it.
 *
 * <p>Was a package-private nested class of {@link AiStreamController}, which meant every streaming
 * endpoint had to live in that one controller to reach it — and so the AI module held an endpoint
 * for mail threads and compiled against the mail module to serve it. The hub is not specific to any
 * domain, and now says so.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiStreamHub {

    private static final String CHANNEL_PREFIX = "ai:stream:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RealtimeChannelRegistry registry;

    public SseEmitter subscribeUser(UUID organizationId, UUID userId) {
        return registry.subscribe(CHANNEL_PREFIX + organizationId + ":" + userId);
    }

    public void publish(UUID organizationId, UUID userId, AiStreamEvent event) {
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
