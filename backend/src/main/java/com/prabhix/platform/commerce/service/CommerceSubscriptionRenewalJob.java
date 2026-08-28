package com.prabhix.platform.commerce.service;

import com.prabhix.platform.billing.service.DunningSchedule;
import com.prabhix.platform.commerce.domain.CommerceEnums;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceSubscription;
import com.prabhix.platform.commerce.repository.CommerceSubscriptionRepository;
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
public class CommerceSubscriptionRenewalJob {

    private static final String LOCK_KEY = "pbx:job:commerce-subscription-renewal";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int BATCH_SIZE = 100;

    private final CommerceSubscriptionRepository subscriptionRepository;
    private final CommerceSubscriptionRenewalService renewalService;
    private final StringRedisTemplate redis;

    @Scheduled(cron = "${prabhix.commerce.subscription-renewal-cron:0 45 8 * * *}")
    public void run() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Commerce subscription renewal job already running on another instance");
            return;
        }
        try {
            renewDue();
            retryDunning();
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    @Transactional
    void renewDue() {
        List<CommerceSubscription> due = subscriptionRepository.findDueForRenewal(
                List.of(CommerceEnums.SubscriptionStatus.ACTIVE),
                Instant.now(),
                PageRequest.of(0, BATCH_SIZE));
        for (CommerceSubscription subscription : due) {
            subscription = subscriptionRepository.lockById(subscription.getId()).orElse(subscription);
            if (subscription.getNextBillingAt() == null
                    || Instant.now().isBefore(subscription.getNextBillingAt())) {
                continue;
            }
            CommerceOrder order = renewalService.createRenewalOrder(subscription);
            var result = renewalService.attemptOffSessionCharge(subscription, order);
            if (!result.charged()) {
                renewalService.markPastDue(subscription);
            }
        }
    }

    @Transactional
    void retryDunning() {
        List<CommerceSubscription> due = subscriptionRepository.findDueForDunningRetry(
                CommerceEnums.SubscriptionStatus.PAST_DUE,
                DunningSchedule.MAX_ATTEMPTS,
                Instant.now(),
                PageRequest.of(0, BATCH_SIZE));
        for (CommerceSubscription subscription : due) {
            subscription = subscriptionRepository.lockById(subscription.getId()).orElse(subscription);
            CommerceOrder order = renewalService.createRenewalOrder(subscription);
            var result = renewalService.attemptOffSessionCharge(subscription, order);
            if (result.charged()) {
                continue;
            }
            int attempt = subscription.getFailedPaymentCount();
            subscription.setFailedPaymentCount(attempt + 1);
            subscription.setNextDunningRetryAt(DunningSchedule.nextRetryAt(attempt + 1));
            if (subscription.getFailedPaymentCount() >= DunningSchedule.MAX_ATTEMPTS) {
                subscription.setStatus(CommerceEnums.SubscriptionStatus.EXPIRED);
            }
            subscriptionRepository.save(subscription);
        }
    }
}
