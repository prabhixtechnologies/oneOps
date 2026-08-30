package com.prabhix.platform.ai.provider.http;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.provider.http.ResilientProviderHttpClient;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResilientProviderHttpClientTest {

    private AtomicInteger calls;
    private ResilientProviderHttpClient client;

    @BeforeEach
    void setUp() {
        calls = new AtomicInteger();
        client = new ResilientProviderHttpClient(request -> {
            calls.incrementAndGet();
            return switch (calls.get()) {
                case 1 -> new ProviderHttpResponse(429, "rate limited");
                case 2 -> new ProviderHttpResponse(200, "ok");
                default -> new ProviderHttpResponse(500, "fail");
            };
        }, new ObjectMapper());
    }

    @Test
    void retriesOn429() {
        ProviderHttpResponse response = client.execute(new ProviderHttpRequest(
                HttpMethod.POST, "http://test", java.util.Map.of(), "{}"));
        assertEquals(200, response.statusCode());
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryOn401() {
        ResilientProviderHttpClient authClient = new ResilientProviderHttpClient(
                request -> new ProviderHttpResponse(401, "unauthorized"), new ObjectMapper());
        ApiException ex = assertThrows(ApiException.class, () -> authClient.execute(
                new ProviderHttpRequest(HttpMethod.POST, "http://test", java.util.Map.of(), "{}")));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED, ex.getCode());
    }

    @Test
    void doesNotRetryOnContentBlock() {
        ResilientProviderHttpClient blockedClient = new ResilientProviderHttpClient(
                request -> new ProviderHttpResponse(400, "{\"error\":\"content_filter\"}"),
                new ObjectMapper());
        ApiException ex = assertThrows(ApiException.class, () -> blockedClient.execute(
                new ProviderHttpRequest(HttpMethod.POST, "http://test", java.util.Map.of(), "{}")));
        assertEquals(ErrorCode.AI_CONTENT_BLOCKED, ex.getCode());
    }
}
