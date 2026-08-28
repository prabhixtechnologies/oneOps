package com.prabhix.platform.ai.service;

import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.domain.AiOrgSettings;
import com.prabhix.platform.ai.domain.AiUsage;
import com.prabhix.platform.ai.prompt.PromptService;
import com.prabhix.platform.ai.provider.AiCapabilities;
import com.prabhix.platform.ai.provider.AiProvider;
import com.prabhix.platform.ai.provider.AiProviderId;
import com.prabhix.platform.ai.provider.AiProviderRegistry;
import com.prabhix.platform.ai.provider.model.AiCompletionRequest;
import com.prabhix.platform.ai.provider.model.AiCompletionResponse;
import com.prabhix.platform.ai.provider.model.AiTokenUsage;
import com.prabhix.platform.ai.repository.AiOrgSettingsRepository;
import com.prabhix.platform.ai.safety.PiiRedactor;
import com.prabhix.platform.ai.usage.AiQuotaEnforcer;
import com.prabhix.platform.ai.usage.AiUsageService;
import com.prabhix.platform.common.spi.EntitlementGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorModelResolutionTest {

    @Mock private AiProviderRegistry providerRegistry;
    @Mock private AiOrgSettingsRepository orgSettingsRepository;
    @Mock private PromptService promptService;
    @Mock private AiQuotaEnforcer quotaEnforcer;
    @Mock private AiUsageService usageService;
    @Mock private EntitlementGate entitlements;
    @Mock private AiProvider provider;

    private AiOrchestrator orchestrator;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                true,
                "gemini",
                Duration.ofSeconds(30),
                2048,
                2_000_000L,
                false,
                Map.of(
                        "gemini", new AiProperties.ProviderConfig(
                                "key", "https://gemini", "gemini-chat", "gemini-reason", "")));
        orchestrator = new AiOrchestrator(
                properties, providerRegistry, orgSettingsRepository, promptService,
                new PiiRedactor(), quotaEnforcer, usageService, entitlements);

        when(providerRegistry.resolve(orgId, null)).thenReturn(provider);
        when(provider.id()).thenReturn(AiProviderId.GEMINI);
        when(provider.capabilities()).thenReturn(new AiCapabilities(false, true, false));
        when(promptService.resolve(any(), any(), any())).thenReturn(
                new PromptService.ResolvedPrompt("chat.sentiment", "text", null, null, 0.1, 1));
        when(provider.complete(any())).thenReturn(
                new AiCompletionResponse("{}", AiTokenUsage.empty(), "stop"));
        when(usageService.record(
                any(), any(), any(), any(), any(), any(), any(int.class), any(), any(boolean.class),
                any(), any(), any(), any())).thenReturn(new AiUsage());

        AiOrgSettings settings = new AiOrgSettings();
        settings.setPreferredChatModel("org-chat-model");
        settings.setPreferredReasoningModel("org-reason-model");
        when(orgSettingsRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(settings));
    }

    @Test
    void reasoningTaskUsesOrgReasoningModel() {
        orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, UUID.randomUUID(), "chat", "chat.sentiment",
                Map.of(), null, null, null, null));

        ArgumentCaptor<AiCompletionRequest> captor = ArgumentCaptor.forClass(AiCompletionRequest.class);
        verify(provider).complete(captor.capture());
        assertEquals("org-reason-model", captor.getValue().model());
        assertNotNull(captor.getValue().structuredOutput());
    }

    @Test
    void chatTaskUsesOrgChatModel() {
        orchestrator.complete(new AiOrchestrator.AiRequest(
                orgId, UUID.randomUUID(), "chat", "chat.reply_suggest",
                Map.of(), null, null, null, null));

        ArgumentCaptor<AiCompletionRequest> captor = ArgumentCaptor.forClass(AiCompletionRequest.class);
        verify(provider).complete(captor.capture());
        assertEquals("org-chat-model", captor.getValue().model());
    }
}
