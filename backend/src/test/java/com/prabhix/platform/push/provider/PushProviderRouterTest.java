package com.prabhix.platform.push.provider;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.push.config.PushProperties;
import com.prabhix.platform.push.provider.noop.LoggingPushProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PushProviderRouterTest {

    @Test
    void defaultsToLoggingProvider() {
        PushProperties properties = new PushProperties(
                "LOGGING",
                new PushProperties.Outbox(true, 50, java.time.Duration.ofSeconds(5), 6),
                new PushProperties.Fcm("", ""),
                new PushProperties.Apns("", "", "", "", "https://api.push.apple.com"));

        PushProviderRouter router = new PushProviderRouter(properties, new ObjectMapper());

        assertInstanceOf(LoggingPushProvider.class, router.select());
        assertEquals("LOGGING", router.select().providerId());
    }
}
