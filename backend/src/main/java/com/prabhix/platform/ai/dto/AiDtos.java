package com.prabhix.platform.ai.dto;

import com.prabhix.platform.ai.domain.AiPrompt;
import com.prabhix.platform.ai.domain.AiUsage;
import com.prabhix.platform.ai.service.AiOrchestrator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AiDtos {

    private AiDtos() {
    }

    public record AvailabilityView(
            boolean enabled,
            boolean configured,
            long tokensUsedThisMonth,
            long monthlyQuota) {

        public static AvailabilityView from(AiOrchestrator.AvailabilityStatus status) {
            return new AvailabilityView(
                    status.enabled(), status.configured(),
                    status.tokensUsedThisMonth(), status.monthlyQuota());
        }
    }

    public record DraftSuggestion(
            boolean available,
            String draft,
            String provider,
            String model,
            boolean piiRedacted,
            boolean unavailableBecauseNotConfigured) {

        public static DraftSuggestion of(String draft, AiOrchestrator.AiResult result) {
            return new DraftSuggestion(
                    result.available(), draft,
                    result.provider().configKey(), result.model(),
                    result.piiRedacted(), false);
        }

        public static DraftSuggestion unavailable(AiOrchestrator.AvailabilityStatus status) {
            return new DraftSuggestion(false, "", null, null, false, !status.configured());
        }
    }

    public record TextResult(
            boolean available,
            String text,
            String provider,
            String model,
            boolean unavailableBecauseNotConfigured) {

        public static TextResult of(String text, AiOrchestrator.AiResult result) {
            return new TextResult(true, text, result.provider().configKey(), result.model(), false);
        }

        public static TextResult unavailable(AiOrchestrator.AvailabilityStatus status) {
            return new TextResult(false, "", null, null, !status.configured());
        }
    }

    public record TriageSuggestion(
            boolean available,
            List<String> suggestedTags,
            String suggestedPriority,
            String intent,
            BigDecimal confidence,
            String provider,
            String model,
            boolean unavailableBecauseNotConfigured) {

        public static TriageSuggestion unavailable(AiOrchestrator.AvailabilityStatus status) {
            return new TriageSuggestion(false, List.of(), null, null, null, null, null, !status.configured());
        }

        public static TriageSuggestion empty() {
            return new TriageSuggestion(false, List.of(), null, null, null, null, null, false);
        }
    }

    public record RewriteRequest(String draft, String action) {
    }

    public record RewriteResult(
            boolean available,
            String text,
            String provider,
            String model) {
    }

    public record SentimentResult(
            boolean available,
            String sentiment,
            String urgency,
            String summary) {
    }

    public record HandoffSummaryResult(
            boolean available,
            UUID noteMessageId,
            String summary) {
    }

    public record LeadEnrichmentResult(
            boolean available,
            int score,
            String priority,
            String summary,
            List<String> suggestedActions) {
    }

    public record ProductDescriptionResult(
            boolean available,
            String description,
            String provider,
            String model) {
    }

    public record AssistRequest(
            String instruction,
            String context,
            String taskKey,
            String providerOverride) {
    }

    public record AssistResult(
            boolean available,
            String text,
            String provider,
            String model) {
    }

    public record UsageSummaryView(long tokensThisMonth, long costPaiseThisMonth) {
    }

    public record UsageRowView(
            UUID id,
            String feature,
            String taskKey,
            String provider,
            String model,
            int totalTokens,
            int latencyMs,
            String outcome,
            long costEstimatePaise,
            boolean piiRedacted,
            Instant createdAt) {

        public static UsageRowView from(AiUsage usage) {
            return new UsageRowView(
                    usage.getId(),
                    usage.getFeature(),
                    usage.getTaskKey(),
                    usage.getProvider(),
                    usage.getModel(),
                    usage.getTotalTokens(),
                    usage.getLatencyMs(),
                    usage.getOutcome().name(),
                    usage.getCostEstimatePaise(),
                    usage.isPiiRedacted(),
                    usage.getCreatedAt());
        }
    }

    public record PromptView(
            UUID id,
            String taskKey,
            String name,
            String description,
            String template,
            String provider,
            String model,
            double temperature,
            int version,
            boolean orgOverride) {

        public static PromptView from(AiPrompt prompt, UUID orgId) {
            return new PromptView(
                    prompt.getId(),
                    prompt.getTaskKey(),
                    prompt.getName(),
                    prompt.getDescription(),
                    prompt.getTemplate(),
                    prompt.getProvider(),
                    prompt.getModel(),
                    prompt.getTemperature(),
                    prompt.getPromptVersion(),
                    orgId != null && orgId.equals(prompt.getOrganizationId()));
        }
    }

    public record UpdatePromptRequest(
            String template,
            String provider,
            String model,
            Double temperature) {
    }

    public record OrgSettingsView(
            String preferredProvider,
            String preferredChatModel,
            String preferredReasoningModel,
            boolean firstResponderEnabled) {
    }

    public record UpdateOrgSettingsRequest(
            String preferredProvider,
            String preferredChatModel,
            String preferredReasoningModel,
            Boolean firstResponderEnabled) {
    }

    public record StreamEventPayload(
            String type,
            String delta,
            boolean finished,
            UUID conversationId,
            UUID threadId) {
    }
}
