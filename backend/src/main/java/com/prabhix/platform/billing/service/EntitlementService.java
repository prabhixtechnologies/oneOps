package com.prabhix.platform.billing.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.spi.EntitlementGate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves what an organization's plan allows, and enforces it.
 *
 * <p>Other modules depend on {@link EntitlementGate} rather than this class, so they never
 * compile against billing.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService implements EntitlementGate {

    private static final String CACHE_PREFIX = "billing:entitlements:";

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository planRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Map<String, Object> entitlementsFor(UUID organizationId) {
        String cached = redis.opsForValue().get(CACHE_PREFIX + organizationId);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                // fall through to reload
            }
        }
        Map<String, Object> resolved = loadEntitlements(organizationId);
        try {
            redis.opsForValue().set(
                    CACHE_PREFIX + organizationId,
                    objectMapper.writeValueAsString(resolved),
                    Duration.ofMinutes(15));
        } catch (Exception ignored) {
            // cache is best-effort
        }
        return resolved;
    }

    @Override
    public void requireQuota(UUID organizationId, String key, long currentUsage) {
        Object limit = entitlementsFor(organizationId).get(key);
        if (limit == null) {
            return;
        }
        long quota = toLong(limit);
        if (quota == -1) {
            return;
        }
        if (currentUsage >= quota) {
            throw ApiException.of(ErrorCode.LIMIT_EXCEEDED,
                    "You have reached the limit for " + key);
        }
    }

    @Override
    public void requireFeature(UUID organizationId, String key) {
        Object value = entitlementsFor(organizationId).get(key);
        if (value instanceof Boolean enabled && !enabled) {
            throw ApiException.of(ErrorCode.FEATURE_DISABLED,
                    "That feature is not available on your current plan");
        }
        if (value instanceof Number number && number.intValue() == 0) {
            throw ApiException.of(ErrorCode.FEATURE_DISABLED,
                    "That feature is not available on your current plan");
        }
    }

    public void requirePlanSeats(BillingPlan plan, int seats) {
        BillingAmountCalculator.computePlanAmountPaise(plan, seats);
    }

    @Override
    public void requireMemberSeat(UUID organizationId, long currentMemberCount) {
        BillingSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatusIn(organizationId, List.of(
                        BillingEnums.SubscriptionStatus.TRIALING,
                        BillingEnums.SubscriptionStatus.ACTIVE,
                        BillingEnums.SubscriptionStatus.PAST_DUE,
                        BillingEnums.SubscriptionStatus.PAUSED))
                .orElseThrow(() -> ApiException.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No active subscription for this organization"));
        if (currentMemberCount >= subscription.getSeats()) {
            throw ApiException.of(ErrorCode.SEAT_LIMIT_REACHED,
                    "You have reached your subscribed seat limit");
        }
    }

    public void evictCache(UUID organizationId) {
        redis.delete(CACHE_PREFIX + organizationId);
    }

    private Map<String, Object> loadEntitlements(UUID organizationId) {
        BillingSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatusIn(organizationId, List.of(
                        BillingEnums.SubscriptionStatus.TRIALING,
                        BillingEnums.SubscriptionStatus.ACTIVE,
                        BillingEnums.SubscriptionStatus.PAST_DUE,
                        BillingEnums.SubscriptionStatus.PAUSED))
                .orElseThrow(() -> ApiException.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No active subscription for this organization"));

        BillingPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));

        return plan.getEntitlements() == null ? Map.of() : plan.getEntitlements();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        return 0;
    }
}
