package com.prabhix.platform.ai.provider.model;

import java.util.List;

public record AiCompletionRequest(
        String model,
        List<AiMessage> messages,
        Double temperature,
        Integer maxOutputTokens,
        AiStructuredOutput structuredOutput,
        boolean stream) {

    public static AiCompletionRequest of(String model, List<AiMessage> messages) {
        return new AiCompletionRequest(model, messages, null, null, null, false);
    }
}
