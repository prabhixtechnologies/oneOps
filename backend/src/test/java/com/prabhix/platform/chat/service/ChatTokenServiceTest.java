package com.prabhix.platform.chat.service;

import com.prabhix.platform.chat.config.ChatProperties;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatTokenServiceTest {

    private ChatTokenService tokenService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID visitorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties.Security.Jwt jwt = new PrabhixProperties.Security.Jwt(
                "dev-only-insecure-secret-change-me-0123456789abcdefghijklmnop",
                "prabhix-platform", Duration.ofMinutes(15), Duration.ofDays(30));
        PrabhixProperties.Security security = new PrabhixProperties.Security(
                jwt, new PrabhixProperties.Security.RateLimit(true, 10, 600),
                new PrabhixProperties.Security.Password(10, 12),
                TestProperties.sessionCookie());
        PrabhixProperties properties = new PrabhixProperties(
                null, null, security, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        ChatProperties chatProperties = new ChatProperties(
                Duration.ofHours(1), 30, List.of("http://localhost:3000"),
                new ChatProperties.BusinessHoursDefaults("09:00", "18:00", "Asia/Kolkata"));
        tokenService = new ChatTokenService(properties, chatProperties);
    }

    @Test
    void tokenIsScopedToConversation() {
        String token = tokenService.issue(orgId, conversationId, visitorId);
        ChatTokenService.ConversationToken parsed = tokenService.parse(token);
        assertEquals(conversationId, parsed.conversationId());

        UUID otherConversation = UUID.randomUUID();
        ApiException ex = assertThrows(ApiException.class,
                () -> tokenService.assertConversation(parsed, otherConversation));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }
}
