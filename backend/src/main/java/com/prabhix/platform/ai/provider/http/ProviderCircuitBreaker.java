package com.prabhix.platform.ai.provider.http;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class ProviderCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 5;
    private static final long COOLDOWN_SECONDS = 30;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant openUntil;

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntil = null;
    }

    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openUntil = Instant.now().plusSeconds(COOLDOWN_SECONDS);
        }
    }

    public void checkOpen() {
        Instant until = openUntil;
        if (until != null && Instant.now().isBefore(until)) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR,
                    "AI provider is temporarily unavailable. Try again shortly.");
        }
        if (until != null && Instant.now().isAfter(until)) {
            openUntil = null;
            consecutiveFailures.set(0);
        }
    }
}
