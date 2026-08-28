package com.prabhix.platform.ai.usage;

import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.repository.AiUsageRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiQuotaEnforcer {

    private final AiProperties properties;
    private final AiUsageRepository usageRepository;

    public void checkQuota(UUID organizationId) {
        if (properties.monthlyTokenQuota() <= 0) {
            return;
        }
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long used = usageRepository.sumTokensSince(organizationId, monthStart);
        if (used >= properties.monthlyTokenQuota()) {
            throw ApiException.of(ErrorCode.AI_QUOTA_EXCEEDED,
                    "Monthly AI token quota exceeded for this organization");
        }
    }

    public long tokensUsedThisMonth(UUID organizationId) {
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return usageRepository.sumTokensSince(organizationId, monthStart);
    }
}
