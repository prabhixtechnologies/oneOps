package com.prabhix.platform.ai.provider.model;

public record AiStreamChunk(String delta, boolean finished, AiTokenUsage usage) {
}
