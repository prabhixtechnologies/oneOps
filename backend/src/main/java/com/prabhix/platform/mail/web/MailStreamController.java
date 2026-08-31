package com.prabhix.platform.mail.web;

import com.prabhix.platform.common.realtime.RealtimeChannelRegistry;
import com.prabhix.platform.mail.event.MailStreamEvent;
import com.prabhix.platform.common.util.Json;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/mail")
@RequiredArgsConstructor
public class MailStreamController {

    private final MailStreamHub hub;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(Authorize.MAIL_READ)
    public SseEmitter stream(@CurrentUser PrabhixPrincipal principal) {
        return hub.subscribe(principal.requireOrganizationId());
    }

    @Component
    @RequiredArgsConstructor
    static class MailStreamHub {

        private static final String CHANNEL_PREFIX = "mail:stream:";

        private final StringRedisTemplate redis;
        private final RealtimeChannelRegistry registry;

        SseEmitter subscribe(UUID organizationId) {
            return registry.subscribe(CHANNEL_PREFIX + organizationId);
        }

        @EventListener
        void onMailStreamEvent(MailStreamEvent event) {
            if (event.organizationId() == null) {
                return;
            }
            String channel = CHANNEL_PREFIX + event.organizationId();
            String json = Json.toJson(Map.of("type", event.type(), "payload", event.payload()));
            redis.convertAndSend(channel, json);
            registry.fanOutLocal(channel, json);
        }
    }
}
