package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.event.MailRequested;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DunningJob {

    private static final String LOCK_KEY = "pbx:job:billing-dunning";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int BATCH_SIZE = 100;

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository planRepository;
    private final BillingOrgReader orgReader;
    private final BillingRenewalService renewalService;
    private final ApplicationEventPublisher events;
    private final PrabhixProperties properties;
    private final StringRedisTemplate redis;

    @Scheduled(cron = "${prabhix.billing.dunning-cron:0 0 9 * * *}")
    public void run() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Dunning job already running on another instance");
            return;
        }
        try {
            processDueRetries();
            processExhausted();
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    @Transactional
    void processDueRetries() {
        List<BillingSubscription> due = subscriptionRepository.findDueForDunningRetry(
                BillingEnums.SubscriptionStatus.PAST_DUE,
                DunningSchedule.MAX_ATTEMPTS,
                Instant.now(),
                PageRequest.of(0, BATCH_SIZE));

        for (BillingSubscription subscription : due) {
            subscription = subscriptionRepository.lockById(subscription.getId()).orElse(subscription);
            int attempt = subscription.getFailedPaymentCount();
            if (attempt >= DunningSchedule.MAX_ATTEMPTS) {
                continue;
            }

            var order = renewalService.createRenewalOrder(subscription);
            var charge = renewalService.attemptOffSessionCharge(subscription, order);
            if (charge.charged()) {
                continue;
            }

            sendFailureMail(subscription, attempt, planName(subscription));
            subscription.setFailedPaymentCount(attempt + 1);
            subscription.setNextDunningRetryAt(DunningSchedule.nextRetryAt(attempt + 1));
            subscription.setGracePeriodEndsAt(subscription.getNextDunningRetryAt());
            subscriptionRepository.save(subscription);
        }
    }

    @Transactional
    void processExhausted() {
        List<BillingSubscription> pastDue = subscriptionRepository.findPastDueBatch(
                BillingEnums.SubscriptionStatus.PAST_DUE,
                PageRequest.of(0, BATCH_SIZE));

        for (BillingSubscription subscription : pastDue) {
            if (subscription.getFailedPaymentCount() < DunningSchedule.MAX_ATTEMPTS) {
                continue;
            }
            if (subscription.getGracePeriodEndsAt() != null
                    && Instant.now().isAfter(subscription.getGracePeriodEndsAt())) {
                subscription = subscriptionRepository.lockById(subscription.getId()).orElse(subscription);
                orgReader.suspendOrganization(subscription.getOrganizationId());
                subscription.setStatus(BillingEnums.SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(subscription);
            }
        }
    }

    private void sendFailureMail(BillingSubscription subscription, int attempt, String planName) {
        long amount = subscription.getLockedAmountPaise()
                + Math.max(0, subscription.getSeats()) * subscription.getLockedPerSeatPaise();
        orgReader.find(subscription.getOrganizationId()).ifPresent(org -> {
            if (org.billingEmail() == null) {
                return;
            }
            events.publishEvent(MailRequested.forOrganization(
                    subscription.getOrganizationId(),
                    org.billingEmail(),
                    "billing.payment-failed",
                    Map.of(
                            "amount", amount,
                            "planName", planName,
                            "graceEndsOn", subscription.getGracePeriodEndsAt() == null
                                    ? "" : subscription.getGracePeriodEndsAt().toString(),
                            "retryUrl", properties.urls().console() + "/settings/billing",
                            "attempt", attempt + 1,
                            "maxAttempts", DunningSchedule.MAX_ATTEMPTS),
                    "dunning-" + subscription.getId() + "-" + attempt));
        });
    }

    private String planName(BillingSubscription subscription) {
        BillingPlan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        return plan == null ? "your plan" : plan.getName();
    }
}
