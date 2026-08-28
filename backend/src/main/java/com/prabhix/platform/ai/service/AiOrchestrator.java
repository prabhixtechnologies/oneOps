package com.prabhix.platform.ai.service;

import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.domain.AiOrgSettings;
import com.prabhix.platform.ai.domain.AiUsage;
import com.prabhix.platform.ai.prompt.PromptService;
import com.prabhix.platform.ai.provider.AiProvider;
import com.prabhix.platform.ai.provider.AiProviderId;
import com.prabhix.platform.ai.provider.AiProviderRegistry;
import com.prabhix.platform.ai.provider.model.AiCompletionRequest;
import com.prabhix.platform.ai.provider.model.AiCompletionResponse;
import com.prabhix.platform.ai.provider.model.AiMessage;
import com.prabhix.platform.ai.provider.model.AiRole;
import com.prabhix.platform.ai.provider.model.AiStreamChunk;
import com.prabhix.platform.ai.provider.model.AiStructuredOutput;
import com.prabhix.platform.ai.provider.model.AiTokenUsage;
import com.prabhix.platform.ai.provider.noop.NoopProvider;
import com.prabhix.platform.ai.repository.AiOrgSettingsRepository;
import com.prabhix.platform.ai.safety.PiiRedactor;
import com.prabhix.platform.ai.usage.AiQuotaEnforcer;
import com.prabhix.platform.ai.usage.AiUsageService;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.spi.EntitlementGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestrator {

    private static final Set<String> REASONING_TASKS = Set.of(
            "mail.thread_triage",
            "chat.sentiment",
            "lead.score_enrich",
            "chat.handoff_summary");

    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-orchestrator-timeout");
        t.setDaemon(true);
        return t;
    });

    private final AiProperties properties;
    private final AiProviderRegistry providerRegistry;
    private final AiOrgSettingsRepository orgSettingsRepository;
    private final PromptService promptService;
    private final PiiRedactor piiRedactor;
    private final AiQuotaEnforcer quotaEnforcer;
    private final AiUsageService usageService;
    private final EntitlementGate entitlements;

    public AiResult complete(AiRequest request) {
        long start = System.currentTimeMillis();
        if (!properties.enabled()) {
            return disabledResult(request, start);
        }
        entitlements.requireFeature(request.organizationId(), "ai");
        try {
            quotaEnforcer.checkQuota(request.organizationId());
            PromptService.ResolvedPrompt prompt = promptService.resolve(
                    request.organizationId(), request.taskKey(), request.variables());
            AiProvider provider = providerRegistry.resolve(
                    request.organizationId(),
                    request.providerOverride() != null ? request.providerOverride() : prompt.providerOverride());
            String userText = prompt.renderedText();
            boolean redacted = false;
            if (properties.redactPii()) {
                PiiRedactor.RedactionResult redaction = piiRedactor.redact(userText);
                userText = redaction.text();
                redacted = redaction.redacted();
            }
            String model = resolveModel(provider, request.organizationId(), request.taskKey(), prompt.modelOverride());
            AiStructuredOutput structured = request.structuredOutput() != null
                    ? request.structuredOutput()
                    : resolveStructuredOutput(request.taskKey(), provider);
            AiCompletionRequest completionRequest = new AiCompletionRequest(
                    model,
                    List.of(new AiMessage(AiRole.USER, userText)),
                    prompt.temperature(),
                    properties.maxOutputTokens(),
                    structured,
                    false);
            AiCompletionResponse response = callWithTimeout(
                    () -> provider.complete(completionRequest), request.taskKey());
            int latency = (int) (System.currentTimeMillis() - start);
            usageService.record(
                    request.organizationId(),
                    request.feature(),
                    request.taskKey(),
                    provider.id().configKey(),
                    model,
                    response.usage(),
                    latency,
                    AiUsage.Outcome.SUCCESS,
                    redacted,
                    request.correlationType(),
                    request.correlationId(),
                    null,
                    request.userId());
            return new AiResult(true, response.text(), response.usage(), provider.id(), model, redacted, null);
        } catch (ApiException ex) {
            recordFailure(request, start, ex);
            throw ex;
        } catch (Exception ex) {
            recordFailure(request, start, ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "AI request failed", ex));
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "AI request failed", ex);
        }
    }

    public void stream(AiRequest request, Consumer<AiStreamChunk> consumer) {
        if (!properties.enabled()) {
            throw ApiException.of(ErrorCode.AI_NOT_CONFIGURED, "AI is disabled");
        }
        entitlements.requireFeature(request.organizationId(), "ai");
        quotaEnforcer.checkQuota(request.organizationId());
        long start = System.currentTimeMillis();
        PromptService.ResolvedPrompt prompt = promptService.resolve(
                request.organizationId(), request.taskKey(), request.variables());
        AiProvider provider = providerRegistry.resolve(
                request.organizationId(),
                request.providerOverride() != null ? request.providerOverride() : prompt.providerOverride());
        if (!provider.capabilities().streaming()) {
            AiResult result = complete(request);
            consumer.accept(new AiStreamChunk(result.text(), true, result.usage()));
            return;
        }
        String userText = prompt.renderedText();
        boolean redacted = false;
        if (properties.redactPii()) {
            PiiRedactor.RedactionResult redaction = piiRedactor.redact(userText);
            userText = redaction.text();
            redacted = redaction.redacted();
        }
        String model = resolveModel(provider, request.organizationId(), request.taskKey(), prompt.modelOverride());
        AiStructuredOutput structured = request.structuredOutput() != null
                ? request.structuredOutput()
                : resolveStructuredOutput(request.taskKey(), provider);
        AiCompletionRequest completionRequest = new AiCompletionRequest(
                model,
                List.of(new AiMessage(AiRole.USER, userText)),
                prompt.temperature(),
                properties.maxOutputTokens(),
                structured,
                true);
        final AiTokenUsage[] lastUsage = {AiTokenUsage.empty()};
        final boolean piiFlag = redacted;
        provider.streamComplete(completionRequest, chunk -> {
            lastUsage[0] = chunk.usage().totalTokens() > 0 ? chunk.usage() : lastUsage[0];
            consumer.accept(chunk);
            if (chunk.finished()) {
                int latency = (int) (System.currentTimeMillis() - start);
                usageService.record(
                        request.organizationId(),
                        request.feature(),
                        request.taskKey(),
                        provider.id().configKey(),
                        model,
                        lastUsage[0],
                        latency,
                        AiUsage.Outcome.SUCCESS,
                        piiFlag,
                        request.correlationType(),
                        request.correlationId(),
                        null,
                        request.userId());
            }
        });
    }

    public AvailabilityStatus availability(UUID organizationId) {
        return new AvailabilityStatus(
                properties.enabled(),
                providerRegistry.isAvailable(organizationId),
                quotaEnforcer.tokensUsedThisMonth(organizationId),
                properties.monthlyTokenQuota());
    }

    private AiResult disabledResult(AiRequest request, long start) {
        usageService.record(
                request.organizationId(),
                request.feature(),
                request.taskKey(),
                AiProviderId.NOOP.configKey(),
                "none",
                AiTokenUsage.empty(),
                (int) (System.currentTimeMillis() - start),
                AiUsage.Outcome.DISABLED,
                false,
                request.correlationType(),
                request.correlationId(),
                ErrorCode.AI_NOT_CONFIGURED,
                request.userId());
        return new AiResult(false, "", AiTokenUsage.empty(), AiProviderId.NOOP, "none", false,
                ErrorCode.AI_NOT_CONFIGURED);
    }

    private void recordFailure(AiRequest request, long start, ApiException ex) {
        try {
            AiUsage.Outcome outcome = ex.getCode() == ErrorCode.AI_CONTENT_BLOCKED
                    ? AiUsage.Outcome.BLOCKED : AiUsage.Outcome.ERROR;
            usageService.record(
                    request.organizationId(),
                    request.feature(),
                    request.taskKey(),
                    properties.defaultProvider(),
                    "unknown",
                    AiTokenUsage.empty(),
                    (int) (System.currentTimeMillis() - start),
                    outcome,
                    false,
                    request.correlationType(),
                    request.correlationId(),
                    ex.getCode(),
                    request.userId());
        } catch (Exception logEx) {
            log.debug("Failed to record AI usage failure: {}", logEx.getMessage());
        }
    }

    private String resolveModel(AiProvider provider, UUID organizationId, String taskKey, String promptModelOverride) {
        if (promptModelOverride != null && !promptModelOverride.isBlank()) {
            return promptModelOverride;
        }
        AiOrgSettings settings = orgSettingsRepository.findByOrganizationId(organizationId).orElse(null);
        AiProperties.ProviderConfig config = properties.provider(provider.id().configKey());
        if (REASONING_TASKS.contains(taskKey)) {
            if (settings != null && settings.getPreferredReasoningModel() != null
                    && !settings.getPreferredReasoningModel().isBlank()) {
                return settings.getPreferredReasoningModel();
            }
            if (config.reasoningModel() != null && !config.reasoningModel().isBlank()) {
                return config.reasoningModel();
            }
        }
        if (settings != null && settings.getPreferredChatModel() != null
                && !settings.getPreferredChatModel().isBlank()) {
            return settings.getPreferredChatModel();
        }
        return config.chatModel();
    }

    private AiStructuredOutput resolveStructuredOutput(String taskKey, AiProvider provider) {
        if (!provider.capabilities().structuredOutput()) {
            return null;
        }
        return switch (taskKey) {
            case "mail.thread_triage" -> AiStructuredSchemas.mailTriage();
            case "chat.sentiment" -> AiStructuredSchemas.chatSentiment();
            case "lead.score_enrich" -> AiStructuredSchemas.leadScore();
            default -> null;
        };
    }

    private <T> T callWithTimeout(Callable<T> call, String taskKey) throws Exception {
        Future<T> future = TIMEOUT_EXECUTOR.submit(call);
        try {
            return future.get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR,
                    "AI request timed out after " + properties.requestTimeout() + " for " + taskKey);
        }
    }

    public record AiRequest(
            UUID organizationId,
            UUID userId,
            String feature,
            String taskKey,
            Map<String, Object> variables,
            String providerOverride,
            com.prabhix.platform.ai.provider.model.AiStructuredOutput structuredOutput,
            String correlationType,
            UUID correlationId) {
    }

    public record AiResult(
            boolean available,
            String text,
            AiTokenUsage usage,
            AiProviderId provider,
            String model,
            boolean piiRedacted,
            ErrorCode errorCode) {
    }

    public record AvailabilityStatus(
            boolean enabled,
            boolean configured,
            long tokensUsedThisMonth,
            long monthlyQuota) {
    }
}
