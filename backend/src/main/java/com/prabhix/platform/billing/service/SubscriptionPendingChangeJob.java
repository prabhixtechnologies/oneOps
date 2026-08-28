package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPendingChangeJob {

    private static final int BATCH_SIZE = 50;

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository planRepository;
    private final EntitlementService entitlementService;
    private final BillingOrgReader orgReader;

    @Scheduled(fixedDelayString = "${prabhix.billing.pending-change-interval:PT1H}")
    @Transactional
    public void applyDueChanges() {
        List<BillingSubscription> due = subscriptionRepository.findDuePendingChanges(
                Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (BillingSubscription subscription : due) {
            applyPendingChange(subscription);
        }
    }

    private void applyPendingChange(BillingSubscription subscription) {
        if (subscription.getPendingChangeAt() == null
                || Instant.now().isBefore(subscription.getPendingChangeAt())) {
            return;
        }

        if (subscription.getPendingPlanId() != null) {
            BillingPlan plan = planRepository.findById(subscription.getPendingPlanId()).orElse(null);
            if (plan != null) {
                subscription.setPlanId(plan.getId());
                subscription.setLockedAmountPaise(plan.getAmountPaise());
                subscription.setLockedPerSeatPaise(plan.getPerSeatPaise());
            }
        }
        if (subscription.getPendingSeats() != null) {
            subscription.setSeats(subscription.getPendingSeats());
        }

        subscription.setPendingPlanId(null);
        subscription.setPendingSeats(null);
        subscription.setPendingChangeAt(null);
        subscriptionRepository.save(subscription);

        orgReader.updateSeatLimit(subscription.getOrganizationId(), subscription.getSeats());
        entitlementService.evictCache(subscription.getOrganizationId());
        log.info("Applied pending subscription change for org {}", subscription.getOrganizationId());
    }
}
