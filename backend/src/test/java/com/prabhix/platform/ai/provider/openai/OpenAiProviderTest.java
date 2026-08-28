package com.prabhix.platform.ai.provider.openai;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiProviderTest {

    private StubTransport transport;
    private OpenAiProvider provider;

    @BeforeEach
    void setUp() {
        transport = new StubTransport();
        AiProperties.ProviderConfig config = new AiProperties.ProviderConfig(
                "sk-test", "https://api.openai.com/v1", "gpt-4o-mini", "gpt-4o", "text-embedding-3-small");
        provider = new OpenAiProvider(config,
                new ResilientProviderHttpClient(transport, new ObjectMapper()), new ObjectMapper());
    }

    @Test
    void completeParsesChatCompletion() {
        transport.enqueue(200, """
                {"choices":[{"message":{"content":"Reply text"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}
                """);
        var response = provider.complete(AiCompletionRequest.of("gpt-4o-mini",
                List.of(new AiMessage(AiRole.USER, "Help"))));
        assertEquals("Reply text", response.text());
        assertEquals(20, response.usage().totalTokens());
        assertTrue(transport.lastRequest.url().endsWith("/chat/completions"));
    }

    @Test
    void contentFilterFinishReasonBlocked() {
        transport.enqueue(200, """
                {"choices":[{"message":{"content":""},"finish_reason":"content_filter"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":0,"total_tokens":1}}
                """);
        ApiException ex = assertThrows(ApiException.class, () -> provider.complete(
                AiCompletionRequest.of("gpt-4o-mini", List.of(new AiMessage(AiRole.USER, "x")))));
        assertEquals(ErrorCode.AI_CONTENT_BLOCKED, ex.getCode());
    }

    static class StubTransport implements ProviderHttpTransport {
        ProviderHttpRequest lastRequest;
        private ProviderHttpResponse response;

        void enqueue(int status, String body) {
            response = new ProviderHttpResponse(status, body);
        }

        @Override
        public ProviderHttpResponse execute(ProviderHttpRequest request) {
            lastRequest = request;
            return response;
        }
    }
}
