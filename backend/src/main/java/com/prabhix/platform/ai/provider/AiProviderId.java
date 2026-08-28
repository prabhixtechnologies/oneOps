package com.prabhix.platform.ai.provider;

public enum AiProviderId {
    GEMINI,
    OPENAI,
    ANTHROPIC,
    NOOP;

    public static AiProviderId fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return GEMINI;
        }
        return switch (value.toLowerCase()) {
            case "openai" -> OPENAI;
            case "anthropic" -> ANTHROPIC;
            case "noop", "none", "disabled" -> NOOP;
            default -> GEMINI;
        };
    }

    public String configKey() {
        return name().toLowerCase();
    }
}
