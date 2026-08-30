package com.prabhix.platform.ai.provider.http;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResilientProviderHttpClient {

    private static final int MAX_RETRIES = 2;
    private static final long BACKOFF_MS = 200;

    private final ProviderHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final ProviderCircuitBreaker circuitBreaker;

    public ResilientProviderHttpClient(ProviderHttpTransport transport, ObjectMapper objectMapper) {
        this(transport, objectMapper, new ProviderCircuitBreaker());
    }

    public ResilientProviderHttpClient(ProviderHttpTransport transport,
                                       ObjectMapper objectMapper,
                                       ProviderCircuitBreaker circuitBreaker) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
    }

    public ProviderHttpResponse execute(ProviderHttpRequest request) {
        circuitBreaker.checkOpen();
        ProviderHttpResponse last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                sleep(BACKOFF_MS * attempt);
            }
            last = transport.execute(request);
            if (last.isSuccess()) {
                circuitBreaker.recordSuccess();
                return last;
            }
            if (isContentBlocked(last)) {
                circuitBreaker.recordFailure();
                throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED,
                        "The AI provider blocked this content for safety reasons.");
            }
            if (last.isAuthFailure()) {
                circuitBreaker.recordFailure();
                throw ApiException.of(ErrorCode.AI_NOT_CONFIGURED,
                        "AI provider credentials are invalid or missing.");
            }
            if (!last.isRetryable()) {
                circuitBreaker.recordFailure();
                throw providerError(last);
            }
        }
        circuitBreaker.recordFailure();
        throw providerError(last);
    }

    public ProviderCircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public static boolean isContentBlocked(ProviderHttpResponse response) {
        String body = response.body() == null ? "" : response.body().toLowerCase();
        return body.contains("blocked")
                || body.contains("safety")
                || body.contains("content_filter")
                || body.contains("prompt_blocked")
                || body.contains("responsible_ai");
    }

    private ApiException providerError(ProviderHttpResponse response) {
        log.warn("AI provider HTTP {}: {}", response.statusCode(), truncate(response.body()));
        return ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "AI provider request failed");
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "AI provider returned invalid JSON", ex);
        }
    }
}
