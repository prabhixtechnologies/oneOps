package com.prabhix.platform.ai.provider.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.provider.http.ProviderHttpRequest;
import com.prabhix.platform.ai.provider.http.ProviderHttpResponse;
import com.prabhix.platform.ai.provider.http.ProviderHttpTransport;
import com.prabhix.platform.ai.provider.http.ResilientProviderHttpClient;
import com.prabhix.platform.ai.provider.model.AiCompletionRequest;
import com.prabhix.platform.ai.provider.model.AiMessage;
import com.prabhix.platform.ai.provider.model.AiRole;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubTransport transport;
    private GeminiProvider provider;

    @BeforeEach
    void setUp() {
        transport = new StubTransport();
        AiProperties.ProviderConfig config = new AiProperties.ProviderConfig(
                "test-key",
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-2.0-flash",
                "gemini-2.0-flash-thinking-exp",
                "text-embedding-004");
        provider = new GeminiProvider(config, new ResilientProviderHttpClient(transport, objectMapper), objectMapper);
    }

    @Test
    void completeParsesTextAndTokenUsage() {
        transport.enqueue(200, """
                {"candidates":[{"content":{"parts":[{"text":"Hello there"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15}}
                """);
        var response = provider.complete(AiCompletionRequest.of("gemini-2.0-flash",
                List.of(new AiMessage(AiRole.USER, "Hi"))));
        assertEquals("Hello there", response.text());
        assertEquals(10, response.usage().promptTokens());
        assertEquals(5, response.usage().completionTokens());
        assertTrue(transport.lastRequest.body().contains("\"role\":\"user\""));
    }

    @Test
    void safetyBlockMapsToContentBlocked() {
        transport.enqueue(200, """
                {"promptFeedback":{"blockReason":"SAFETY"},"candidates":[]}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> provider.complete(
                AiCompletionRequest.of("gemini-2.0-flash", List.of(new AiMessage(AiRole.USER, "bad")))));
        assertEquals(ErrorCode.AI_CONTENT_BLOCKED, ex.getCode());
    }

    @Test
    void buildPayloadMapsSystemInstruction() {
        String payload = provider.buildPayload(new AiCompletionRequest(
                "gemini-2.0-flash",
                List.of(
                        new AiMessage(AiRole.SYSTEM, "Be helpful"),
                        new AiMessage(AiRole.USER, "Question")),
                0.5, 100, null, false), false);
        assertTrue(payload.contains("systemInstruction"));
        assertTrue(payload.contains("Be helpful"));
    }

    static class StubTransport implements ProviderHttpTransport {
        private final java.util.Queue<ProviderHttpResponse> responses = new java.util.ArrayDeque<>();
        ProviderHttpRequest lastRequest;
        final AtomicInteger calls = new AtomicInteger();

        void enqueue(int status, String body) {
            responses.add(new ProviderHttpResponse(status, body));
        }

        @Override
        public ProviderHttpResponse execute(ProviderHttpRequest request) {
            calls.incrementAndGet();
            lastRequest = request;
            ProviderHttpResponse next = responses.poll();
            return next != null ? next : new ProviderHttpResponse(500, "empty");
        }
    }
}
