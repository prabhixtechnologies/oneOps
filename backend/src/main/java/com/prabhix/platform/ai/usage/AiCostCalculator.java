package com.prabhix.platform.ai.usage;

import com.prabhix.platform.ai.domain.AiModelRate;
import com.prabhix.platform.ai.provider.model.AiTokenUsage;
import com.prabhix.platform.ai.repository.AiModelRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class AiCostCalculator {

    private final AiModelRateRepository rateRepository;

    public long estimatePaise(String provider, String model, AiTokenUsage usage) {
        if (usage == null) {
            return 0;
        }
        return rateRepository.findByProviderAndModel(provider, model)
                .map(rate -> compute(rate, usage))
                .orElse(0L);
    }

    private long compute(AiModelRate rate, AiTokenUsage usage) {
        BigDecimal promptCost = rate.getPromptPaisePerMillion()
                .multiply(BigDecimal.valueOf(usage.promptTokens()))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        BigDecimal completionCost = rate.getCompletionPaisePerMillion()
                .multiply(BigDecimal.valueOf(usage.completionTokens()))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        return promptCost.add(completionCost).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
