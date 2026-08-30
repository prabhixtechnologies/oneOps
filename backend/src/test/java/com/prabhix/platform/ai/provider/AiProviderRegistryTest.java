package com.prabhix.platform.ai.provider;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.provider.http.RestClientProviderHttpTransport;
import com.prabhix.platform.ai.repository.AiOrgSettingsRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderRegistryTest {

    @Mock private RestClientProviderHttpTransport transport;
    @Mock private AiOrgSettingsRepository orgSettingsRepository;

    private AiProviderRegistry registry;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                true, "gemini", Duration.ofSeconds(30), 2048, 2_000_000L, true,
                Map.of(
                        "gemini", new AiProperties.ProviderConfig("", "https://gemini", "gemini-2.0-flash", "", ""),
                        "openai", new AiProperties.ProviderConfig("sk-test", "https://openai", "gpt-4o-mini", "", "")));
        registry = new AiProviderRegistry(properties, new ObjectMapper(), transport, orgSettingsRepository);
    }

    @Test
    void unconfiguredDefaultProviderThrowsNotConfigured() {
        ApiException ex = assertThrows(ApiException.class, () -> registry.resolve(orgId));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED, ex.getCode());
    }

    @Test
    void perOrgProviderOverrideUsed() {
        AiProvider provider = registry.resolve(orgId, "openai");
        assertEquals(AiProviderId.OPENAI, provider.id());
        assertEquals(true, provider.configured());
    }

    @Test
    void disabledGloballyReturnsNoopViaResolveIfAvailable() {
        AiProperties disabled = new AiProperties(
                false, "gemini", Duration.ofSeconds(30), 2048, 2_000_000L, true,
                Map.of("gemini", new AiProperties.ProviderConfig("key", "url", "m", "", "")));
        AiProviderRegistry disabledRegistry = new AiProviderRegistry(
                disabled, new ObjectMapper(), transport, orgSettingsRepository);
        assertFalse(disabledRegistry.isAvailable(orgId));
        assertEquals(AiProviderId.NOOP, disabledRegistry.resolveIfAvailable(orgId).id());
    }
}
