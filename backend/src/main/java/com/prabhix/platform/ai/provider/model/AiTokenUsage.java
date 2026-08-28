package com.prabhix.platform.ai.provider.model;

public record AiTokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    public static AiTokenUsage empty() {
        return new AiTokenUsage(0, 0, 0);
    }
}
