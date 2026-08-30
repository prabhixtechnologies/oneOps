package com.prabhix.platform.ai.provider;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.provider.anthropic.AnthropicProvider;
import com.prabhix.platform.ai.provider.gemini.GeminiProvider;
import com.prabhix.platform.ai.provider.http.ResilientProviderHttpClient;
import com.prabhix.platform.ai.provider.http.RestClientProviderHttpTransport;
import com.prabhix.platform.ai.provider.noop.NoopProvider;
import com.prabhix.platform.ai.provider.openai.OpenAiProvider;
import com.prabhix.platform.ai.repository.AiOrgSettingsRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiProviderRegistry {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClientProviderHttpTransport transport;
    private final AiOrgSettingsRepository orgSettingsRepository;

    private volatile Map<AiProviderId, AiProvider> providers;

    public AiProvider resolve(UUID organizationId) {
        return resolve(organizationId, null);
    }

    public AiProvider resolve(UUID organizationId, String providerOverride) {
        if (!properties.enabled()) {
            return noop();
        }
        String providerKey = providerOverride;
        if (providerKey == null || providerKey.isBlank()) {
            providerKey = orgSettingsRepository.findByOrganizationId(organizationId)
                    .map(s -> s.getPreferredProvider())
                    .orElse(null);
        }
        if (providerKey == null || providerKey.isBlank()) {
            providerKey = properties.defaultProvider();
        }
        AiProviderId id = AiProviderId.fromConfig(providerKey);
        AiProvider provider = provider(id);
        if (!provider.configured()) {
            throw ApiException.of(ErrorCode.AI_NOT_CONFIGURED,
                    "AI provider '" + id.configKey() + "' is not configured");
        }
        return provider;
    }

    public AiProvider resolveIfAvailable(UUID organizationId) {
        try {
            return resolve(organizationId);
        } catch (ApiException ex) {
            if (ex.getCode() == ErrorCode.AI_NOT_CONFIGURED) {
                return noop();
            }
            throw ex;
        }
    }

    public boolean isAvailable(UUID organizationId) {
        if (!properties.enabled()) {
            return false;
        }
        try {
            return resolve(organizationId).configured();
        } catch (ApiException ex) {
            return false;
        }
    }

    public AiProvider provider(AiProviderId id) {
        return providers().getOrDefault(id, noop());
    }

    private AiProvider noop() {
        return providers().get(AiProviderId.NOOP);
    }

    private Map<AiProviderId, AiProvider> providers() {
        Map<AiProviderId, AiProvider> cached = providers;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (providers == null) {
                ResilientProviderHttpClient http = new ResilientProviderHttpClient(transport, objectMapper);
                Map<AiProviderId, AiProvider> map = new EnumMap<>(AiProviderId.class);
                map.put(AiProviderId.GEMINI, new GeminiProvider(properties.provider("gemini"), http, objectMapper));
                map.put(AiProviderId.OPENAI, new OpenAiProvider(properties.provider("openai"), http, objectMapper));
                map.put(AiProviderId.ANTHROPIC, new AnthropicProvider(properties.provider("anthropic"), http, objectMapper));
                map.put(AiProviderId.NOOP, new NoopProvider());
                providers = map;
            }
            return providers;
        }
    }
}
