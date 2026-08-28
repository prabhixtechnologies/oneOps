package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionRenewalJob {

    private static final String LOCK_KEY = "pbx:job:billing-renewal";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int BATCH_SIZE = 100;

    private static final List<BillingEnums.SubscriptionStatus> RENEWABLE = List.of(
            BillingEnums.SubscriptionStatus.ACTIVE);

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingRenewalService renewalService;
    private final EntitlementService entitlementService;
    private final StringRedisTemplate redis;

    @Scheduled(cron = "${prabhix.billing.renewal-cron:0 30 8 * * *}")
    public void run() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Subscription renewal job already running on another instance");
            return;
        }
        try {
            renewDueSubscriptions();
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    @Transactional
    void renewDueSubscriptions() {
        List<BillingSubscription> due = subscriptionRepository.findDueForRenewal(
                RENEWABLE, Instant.now(), PageRequest.of(0, BATCH_SIZE));

        for (BillingSubscription subscription : due) {
            subscription = subscriptionRepository.lockById(subscription.getId()).orElse(subscription);
            if (subscription.getNextBillingAt() == null
                    || Instant.now().isBefore(subscription.getNextBillingAt())) {
                continue;
            }
            if (subscription.isCancelAtPeriodEnd()) {
                subscription.setStatus(BillingEnums.SubscriptionStatus.CANCELLED);
                subscription.setCancelledAt(Instant.now());
                subscriptionRepository.save(subscription);
                entitlementService.evictCache(subscription.getOrganizationId());
                continue;
            }

            BillingOrder order = renewalService.createRenewalOrder(subscription);
            var charge = renewalService.attemptOffSessionCharge(subscription, order);
            if (!charge.charged()) {
                subscription.setStatus(BillingEnums.SubscriptionStatus.PAST_DUE);
                subscription.setFailedPaymentCount(Math.max(1, subscription.getFailedPaymentCount()));
                subscription.setGracePeriodEndsAt(DunningSchedule.initialGraceEnd());
                subscription.setNextDunningRetryAt(DunningSchedule.initialGraceEnd());
                subscriptionRepository.save(subscription);
                entitlementService.evictCache(subscription.getOrganizationId());
            }
        }
    }
}
