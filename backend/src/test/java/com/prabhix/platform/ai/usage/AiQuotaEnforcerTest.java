package com.prabhix.platform.ai.usage;

import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.repository.AiUsageRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuotaEnforcerTest {

    @Mock private AiUsageRepository usageRepository;

    private AiQuotaEnforcer enforcer;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                true, "gemini", Duration.ofSeconds(30), 2048, 1000L, true, Map.of());
        enforcer = new AiQuotaEnforcer(properties, usageRepository);
    }

    @Test
    void rejectsWhenQuotaExceeded() {
        when(usageRepository.sumTokensSince(eq(orgId), any())).thenReturn(1000L);
        ApiException ex = assertThrows(ApiException.class, () -> enforcer.checkQuota(orgId));
        assertEquals(ErrorCode.AI_QUOTA_EXCEEDED, ex.getCode());
    }

    @Test
    void allowsWhenUnderQuota() {
        when(usageRepository.sumTokensSince(eq(orgId), any())).thenReturn(999L);
        enforcer.checkQuota(orgId);
    }
}
