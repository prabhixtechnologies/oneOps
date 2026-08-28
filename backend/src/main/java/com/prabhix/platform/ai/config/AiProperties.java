package com.prabhix.platform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "prabhix.ai")
public record AiProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("gemini") String defaultProvider,
        @DefaultValue("PT30S") Duration requestTimeout,
        @DefaultValue("2048") int maxOutputTokens,
        @DefaultValue("2000000") long monthlyTokenQuota,
        @DefaultValue("true") boolean redactPii,
        @DefaultValue Map<String, ProviderConfig> providers) {

    public record ProviderConfig(
            @DefaultValue("") String apiKey,
            @DefaultValue("") String baseUrl,
            @DefaultValue("") String chatModel,
            @DefaultValue("") String reasoningModel,
            @DefaultValue("") String embeddingModel) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank();
        }
    }

    public ProviderConfig provider(String id) {
        if (providers == null) {
            return new ProviderConfig("", "", "", "", "");
        }
        ProviderConfig config = providers.get(id);
        return config != null ? config : new ProviderConfig("", "", "", "", "");
    }
}
