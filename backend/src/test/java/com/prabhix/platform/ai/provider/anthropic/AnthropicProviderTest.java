package com.prabhix.platform.ai.provider.anthropic;

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

class AnthropicProviderTest {

    private StubTransport transport;
    private AnthropicProvider provider;

    @BeforeEach
    void setUp() {
        transport = new StubTransport();
        AiProperties.ProviderConfig config = new AiProperties.ProviderConfig(
                "ant-key", "https://api.anthropic.com/v1", "claude-3-5-haiku-latest",
                "claude-3-5-sonnet-latest", "");
        provider = new AnthropicProvider(config,
                new ResilientProviderHttpClient(transport, new ObjectMapper()), new ObjectMapper());
    }

    @Test
    void completeParsesMessagesResponse() {
        transport.enqueue(200, """
                {"content":[{"type":"text","text":"Claude says hi"}],
                 "usage":{"input_tokens":11,"output_tokens":6},"stop_reason":"end_turn"}
                """);
        var response = provider.complete(AiCompletionRequest.of("claude-3-5-haiku-latest",
                List.of(new AiMessage(AiRole.USER, "Hi"))));
        assertEquals("Claude says hi", response.text());
        assertEquals(17, response.usage().totalTokens());
    }

    @Test
    void embedNotSupported() {
        ApiException ex = assertThrows(ApiException.class, () -> provider.embed(
                new com.prabhix.platform.ai.provider.model.AiEmbeddingRequest("model", "text")));
        assertEquals(ErrorCode.AI_MODEL_NOT_SUPPORTED, ex.getCode());
    }

    @Test
    void buildRequestUsesSystemField() throws Exception {
        ProviderHttpRequest req = provider.buildRequest(new AiCompletionRequest(
                "claude-3-5-haiku-latest",
                List.of(new AiMessage(AiRole.SYSTEM, "Rules"), new AiMessage(AiRole.USER, "Q")),
                null, 512, null, false), false);
        assertTrue(req.body().contains("\"system\":\"Rules\""));
        assertTrue(req.headers().containsKey("x-api-key"));
    }

    static class StubTransport implements ProviderHttpTransport {
        private ProviderHttpResponse response;

        void enqueue(int status, String body) {
            response = new ProviderHttpResponse(status, body);
        }

        @Override
        public ProviderHttpResponse execute(ProviderHttpRequest request) {
            return response;
        }
    }
}
