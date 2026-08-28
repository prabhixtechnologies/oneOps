package com.prabhix.platform.ai.provider.model;

public record AiCompletionResponse(
        String text,
        AiTokenUsage usage,
        String finishReason) {
}
