package com.prabhix.platform.ai.provider;

public record AiCapabilities(
        boolean streaming,
        boolean structuredOutput,
        boolean embeddings) {
}
