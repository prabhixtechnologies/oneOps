package com.prabhix.platform.billing.service;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    @Mock
    private BillingSubscriptionRepository subscriptionRepository;
    @Mock
    private BillingPlanRepository planRepository;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private EntitlementService entitlementService;

    @BeforeEach
    void setUp() {
        entitlementService = new EntitlementService(
                subscriptionRepository, planRepository, redis, new ObjectMapper());
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
    }

    @Test
    void minusOneMeansUnlimited() {
        UUID orgId = UUID.randomUUID();
        stubEntitlements(orgId, Map.of("mailboxes", -1));

        assertDoesNotThrow(() -> entitlementService.requireQuota(orgId, "mailboxes", 999_999));
    }

    @Test
    void quotaExceededThrows() {
        UUID orgId = UUID.randomUUID();
        stubEntitlements(orgId, Map.of("mailboxes", 5));

        ApiException ex = assertThrows(ApiException.class,
                () -> entitlementService.requireQuota(orgId, "mailboxes", 5));
        assertEquals(ErrorCode.LIMIT_EXCEEDED, ex.getCode());
    }

    private void stubEntitlements(UUID orgId, Map<String, Object> entitlements) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setPlanId(UUID.randomUUID());
        subscription.setOrganizationId(orgId);
        subscription.setStatus(BillingEnums.SubscriptionStatus.ACTIVE);
        subscription.setSeats(5);
        subscription.setCurrentPeriodStart(java.time.Instant.now());
        subscription.setCurrentPeriodEnd(java.time.Instant.now().plusSeconds(86400));
        subscription.setLockedAmountPaise(0);
        subscription.setCancelAtPeriodEnd(false);

        BillingPlan plan = new BillingPlan();
        plan.setEntitlements(entitlements);

        when(subscriptionRepository.findByOrganizationIdAndStatusIn(orgId, List.of(
                BillingEnums.SubscriptionStatus.TRIALING,
                BillingEnums.SubscriptionStatus.ACTIVE,
                BillingEnums.SubscriptionStatus.PAST_DUE,
                BillingEnums.SubscriptionStatus.PAUSED))).thenReturn(Optional.of(subscription));
        when(planRepository.findById(subscription.getPlanId())).thenReturn(Optional.of(plan));
    }
}
