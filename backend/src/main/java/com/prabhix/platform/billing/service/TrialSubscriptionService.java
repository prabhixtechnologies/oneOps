package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.OrganizationCreated;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Opens the trial {@link BillingSubscription} for a newly registered organization.
 *
 * <p>Without a subscription row every entitlement lookup fails as {@code SUBSCRIPTION_INACTIVE},
 * so a brand new tenant would be locked out of the product it just signed up for.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrialSubscriptionService {

    private static final String DEFAULT_TRIAL_PLAN_KEY = "starter-monthly";

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository planRepository;
    private final EntitlementService entitlementService;
    private final BillingOrgReader orgReader;
    private final PrabhixProperties properties;

    /**
     * The publishing transaction has already committed, so this needs one of its own. It also
     * cannot delegate to {@link #createTrialSubscription}: that is a self-invocation and would
     * bypass the proxy, leaving the write without a transaction.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrganizationCreated(OrganizationCreated event) {
        try {
            createTrial(event.organizationId());
        } catch (RuntimeException e) {
            // Signup has already succeeded; failing here must not surface as a failed
            // registration. The organization is recoverable via the billing screen.
            log.error("Could not open trial subscription for organization {}",
                    event.organizationId(), e);
        }
    }

    @Transactional
    public BillingSubscription createTrialSubscription(UUID organizationId) {
        return createTrial(organizationId);
    }

    private BillingSubscription createTrial(UUID organizationId) {
        var existing = subscriptionRepository.findByOrganizationIdAndStatusIn(organizationId, List.of(
                BillingEnums.SubscriptionStatus.TRIALING,
                BillingEnums.SubscriptionStatus.ACTIVE,
                BillingEnums.SubscriptionStatus.PAST_DUE,
                BillingEnums.SubscriptionStatus.PAUSED));
        if (existing.isPresent()) {
            return existing.get();
        }

        BillingPlan plan = planRepository.findByPlanKey(DEFAULT_TRIAL_PLAN_KEY)
                .filter(BillingPlan::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND,
                        "Default trial plan was not found"));

        int trialDays = properties.billing().trialDays();
        Instant now = Instant.now();
        Instant trialEnds = now.plus(trialDays, ChronoUnit.DAYS);

        BillingSubscription subscription = new BillingSubscription();
        subscription.setOrganizationId(organizationId);
        subscription.setPlanId(plan.getId());
        subscription.setStatus(BillingEnums.SubscriptionStatus.TRIALING);
        subscription.setSeats(plan.getIncludedSeats());
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(trialEnds);
        subscription.setTrialEndsAt(trialEnds);
        subscription.setNextBillingAt(trialEnds);
        subscription.setLockedAmountPaise(plan.getAmountPaise());
        subscription.setLockedPerSeatPaise(plan.getPerSeatPaise());
        subscription.setCancelAtPeriodEnd(false);
        subscription.setFailedPaymentCount(0);
        subscription = subscriptionRepository.save(subscription);

        orgReader.updateSeatLimit(organizationId, plan.getIncludedSeats());
        entitlementService.evictCache(organizationId);
        return subscription;
    }
}
