package com.prabhix.platform.ai.provider;

import com.prabhix.platform.ai.provider.model.AiCompletionRequest;
import com.prabhix.platform.ai.provider.model.AiCompletionResponse;
import com.prabhix.platform.ai.provider.model.AiEmbeddingRequest;
import com.prabhix.platform.ai.provider.model.AiEmbeddingResponse;
import com.prabhix.platform.ai.provider.model.AiStreamChunk;

import java.util.function.Consumer;

public interface AiProvider {

    AiProviderId id();

    AiCapabilities capabilities();

    boolean configured();

    AiCompletionResponse complete(AiCompletionRequest request);

    void streamComplete(AiCompletionRequest request, Consumer<AiStreamChunk> consumer);

    AiEmbeddingResponse embed(AiEmbeddingRequest request);
}
