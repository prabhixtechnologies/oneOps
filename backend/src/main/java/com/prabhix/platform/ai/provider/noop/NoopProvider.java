package com.prabhix.platform.ai.provider.noop;

import com.prabhix.platform.ai.provider.AiCapabilities;
import com.prabhix.platform.ai.provider.AiProvider;
import com.prabhix.platform.ai.provider.AiProviderId;
import com.prabhix.platform.ai.provider.model.AiCompletionRequest;
import com.prabhix.platform.ai.provider.model.AiCompletionResponse;
import com.prabhix.platform.ai.provider.model.AiEmbeddingRequest;
import com.prabhix.platform.ai.provider.model.AiEmbeddingResponse;
import com.prabhix.platform.ai.provider.model.AiStreamChunk;
import com.prabhix.platform.ai.provider.model.AiTokenUsage;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;

import java.util.function.Consumer;

public class NoopProvider implements AiProvider {

    @Override
    public AiProviderId id() {
        return AiProviderId.NOOP;
    }

    @Override
    public AiCapabilities capabilities() {
        return new AiCapabilities(false, false, false);
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public AiCompletionResponse complete(AiCompletionRequest request) {
        throw notConfigured();
    }

    @Override
    public void streamComplete(AiCompletionRequest request, Consumer<AiStreamChunk> consumer) {
        throw notConfigured();
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        throw notConfigured();
    }

    private ApiException notConfigured() {
        return ApiException.of(ErrorCode.AI_NOT_CONFIGURED,
                "AI is not configured on this environment");
    }

    public static AiCompletionResponse disabledPlaceholder() {
        return new AiCompletionResponse("", AiTokenUsage.empty(), "disabled");
    }
}
