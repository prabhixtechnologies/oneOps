package com.prabhix.platform.ai.service;

import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.domain.AiUsage;
import com.prabhix.platform.ai.repository.AiOrgSettingsRepository;
import com.prabhix.platform.ai.prompt.PromptService;
import com.prabhix.platform.ai.provider.AiProviderRegistry;
import com.prabhix.platform.ai.safety.PiiRedactor;
import com.prabhix.platform.ai.usage.AiQuotaEnforcer;
import com.prabhix.platform.ai.usage.AiUsageService;
import com.prabhix.platform.common.spi.EntitlementGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorDisabledTest {

    @Mock private AiProviderRegistry providerRegistry;
    @Mock private AiOrgSettingsRepository orgSettingsRepository;
    @Mock private PromptService promptService;
    @Mock private AiQuotaEnforcer quotaEnforcer;
    @Mock private AiUsageService usageService;
    @Mock private EntitlementGate entitlements;

    private AiOrchestrator orchestrator;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                false, "gemini", Duration.ofSeconds(30), 2048, 2_000_000L, true, Map.of());
        orchestrator = new AiOrchestrator(
                properties, providerRegistry, orgSettingsRepository, promptService, new PiiRedactor(),
                quotaEnforcer, usageService, entitlements);
    }

    @Test
    void completeWhenDisabledReturnsUnavailableResult() {
        when(usageService.record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiUsage());

        var result = orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, UUID.randomUUID(), "mail", "mail.reply_suggest",
                Map.of("subject", "Hi"), null, null, null, null));

        assertFalse(result.available());
        verify(usageService).record(
                org.mockito.ArgumentMatchers.eq(orgId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(AiUsage.Outcome.DISABLED),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void availabilityReflectsDisabledState() {
        var status = orchestrator.availability(orgId);
        assertFalse(status.enabled());
        assertFalse(status.configured());
    }
}
