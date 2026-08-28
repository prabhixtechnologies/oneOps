package com.prabhix.platform.ai.provider.model;

import java.util.List;

public record AiEmbeddingResponse(List<Double> vector, AiTokenUsage usage) {
}
