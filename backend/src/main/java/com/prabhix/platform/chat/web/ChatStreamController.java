package com.prabhix.platform.chat.web;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.chat.event.ChatStreamEvent;
import com.prabhix.platform.chat.event.ChatVisitorStreamEvent;
import com.prabhix.platform.chat.service.ChatTokenService;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.realtime.RealtimeChannelRegistry;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/oneops/chat")
@RequiredArgsConstructor
public class ChatStreamController {

    private final ChatStreamHub hub;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.CHAT_READ)
    public SseEmitter streamAgent(@CurrentUser PrabhixPrincipal principal) {
        return hub.subscribeOrg(principal.requireOrganizationId());
    }

    @GetMapping(value = "/public/visitor-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamVisitorPresence(@RequestParam UUID organizationId,
                                            @RequestParam UUID visitorId,
                                            @RequestParam(required = false) String visitorKey) {
        return hub.subscribeVisitorPresence(organizationId, visitorId, visitorKey);
    }

    @GetMapping(value = "/public/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamVisitor(@RequestParam UUID organizationId,
                                    @RequestParam UUID conversationId,
                                    @RequestParam String token) {
        return hub.subscribeVisitor(organizationId, conversationId, token);
    }

    @Component
    @RequiredArgsConstructor
    static class ChatStreamHub {

        private static final String CHANNEL_ORG_PREFIX = "chat:stream:org:";
        private static final String CHANNEL_CONV_PREFIX = "chat:stream:conv:";
        private static final String CHANNEL_VISITOR_PREFIX = "chat:stream:visitor:";

        private final StringRedisTemplate redis;
        private final ObjectMapper objectMapper;
        private final ChatTokenService tokenService;
        private final RealtimeChannelRegistry registry;
        private final VisitorRepository visitorRepository;

        SseEmitter subscribeOrg(UUID organizationId) {
            return registry.subscribe(CHANNEL_ORG_PREFIX + organizationId);
        }

        SseEmitter subscribeVisitor(UUID organizationId, UUID conversationId, String token) {
            ChatTokenService.ConversationToken parsed = tokenService.parse(token);
            if (!parsed.organizationId().equals(organizationId)
                    || !parsed.conversationId().equals(conversationId)) {
                throw ApiException.of(ErrorCode.FORBIDDEN, "Conversation token does not match");
            }
            return registry.subscribe(CHANNEL_CONV_PREFIX + organizationId + ":" + conversationId);
        }

        SseEmitter subscribeVisitorPresence(UUID organizationId, UUID visitorId, String visitorKey) {
            var visitor = visitorRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(visitorId, organizationId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.FORBIDDEN, "Visitor stream is not available"));
            String storedKey = visitor.getExternalKey();
            if (storedKey == null || storedKey.isBlank()) {
                throw ApiException.of(ErrorCode.FORBIDDEN, "Visitor stream is not available");
            }
            byte[] expected = storedKey.getBytes(StandardCharsets.UTF_8);
            byte[] presented = visitorKey == null ? new byte[0] : visitorKey.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, presented)) {
                throw ApiException.of(ErrorCode.FORBIDDEN, "Visitor stream is not available");
            }
            return registry.subscribe(CHANNEL_VISITOR_PREFIX + organizationId + ":" + visitorId);
        }

        @EventListener
        void onChatVisitorStreamEvent(ChatVisitorStreamEvent event) {
            if (event.organizationId() == null || event.visitorId() == null) {
                return;
            }
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", event.type(),
                        "visitorId", event.visitorId(),
                        "payload", event.payload()));
                String channel = CHANNEL_VISITOR_PREFIX + event.organizationId() + ":" + event.visitorId();
                redis.convertAndSend(channel, json);
                registry.fanOutLocal(channel, json);
            } catch (Exception ex) {
                log.debug("Failed to fan-out visitor chat stream event: {}", ex.getMessage());
            }
        }

        @EventListener
        void onChatStreamEvent(ChatStreamEvent event) {
            if (event.organizationId() == null) {
                return;
            }
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", event.type(),
                        "conversationId", event.conversationId(),
                        "payload", event.payload()));
                String orgChannel = CHANNEL_ORG_PREFIX + event.organizationId();
                redis.convertAndSend(orgChannel, json);
                registry.fanOutLocal(orgChannel, json);
                if (event.conversationId() != null) {
                    String convChannel = CHANNEL_CONV_PREFIX + event.organizationId() + ":" + event.conversationId();
                    redis.convertAndSend(convChannel, json);
                    registry.fanOutLocal(convChannel, json);
                }
            } catch (Exception ex) {
                log.debug("Failed to fan-out chat stream event: {}", ex.getMessage());
            }
        }
    }
}
