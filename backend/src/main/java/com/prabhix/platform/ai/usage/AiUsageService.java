package com.prabhix.platform.ai.usage;

import com.prabhix.platform.ai.domain.AiUsage;
import com.prabhix.platform.ai.provider.model.AiTokenUsage;
import com.prabhix.platform.ai.repository.AiUsageRepository;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiUsageService {

    private final AiUsageRepository usageRepository;
    private final AiCostCalculator costCalculator;
    private final PrabhixProperties properties;

    @Transactional
    public AiUsage record(UUID organizationId,
                          String feature,
                          String taskKey,
                          String provider,
                          String model,
                          AiTokenUsage usage,
                          int latencyMs,
                          AiUsage.Outcome outcome,
                          boolean piiRedacted,
                          String correlationType,
                          UUID correlationId,
                          ErrorCode errorCode,
                          UUID userId) {
        AiUsage row = new AiUsage();
        row.setOrganizationId(organizationId);
        row.setFeature(feature);
        row.setTaskKey(taskKey);
        row.setProvider(provider);
        row.setModel(model);
        row.setPromptTokens(usage != null ? usage.promptTokens() : 0);
        row.setCompletionTokens(usage != null ? usage.completionTokens() : 0);
        row.setTotalTokens(usage != null ? usage.totalTokens() : 0);
        row.setLatencyMs(latencyMs);
        row.setOutcome(outcome);
        row.setPiiRedacted(piiRedacted);
        row.setCorrelationType(correlationType);
        row.setCorrelationId(correlationId);
        row.setCostEstimatePaise(costCalculator.estimatePaise(provider, model, usage));
        if (errorCode != null) {
            row.setErrorCode(errorCode.name());
        }
        return usageRepository.save(row);
    }

    @Transactional(readOnly = true)
    public CursorPage<AiUsage> list(UUID organizationId, String cursor, Integer limit) {
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        var rows = usageRepository.listWithCursor(
                organizationId, decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(rows, pageSize - 1, u -> u, u -> Cursor.of(u.getCreatedAt(), u.getId()).encode());
    }

    @Transactional(readOnly = true)
    public UsageSummary summary(UUID organizationId) {
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new UsageSummary(
                usageRepository.sumTokensSince(organizationId, monthStart),
                usageRepository.sumCostSince(organizationId, monthStart));
    }

    public record UsageSummary(long tokensThisMonth, long costPaiseThisMonth) {
    }
}
