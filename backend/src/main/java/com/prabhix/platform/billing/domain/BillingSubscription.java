package com.prabhix.platform.billing.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "billing_subscriptions")
public class BillingSubscription extends TenantScopedEntity {

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private BillingEnums.SubscriptionStatus status = BillingEnums.SubscriptionStatus.TRIALING;

    @Column(name = "seats", nullable = false)
    private int seats = 5;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    @Column(name = "failed_payment_count", nullable = false)
    private int failedPaymentCount;

    @Column(name = "last_payment_at")
    private Instant lastPaymentAt;

    @Column(name = "next_billing_at")
    private Instant nextBillingAt;

    @Column(name = "razorpay_subscription_id", length = 80)
    private String razorpaySubscriptionId;

    @Column(name = "razorpay_customer_id", length = 80)
    private String razorpayCustomerId;

    @Column(name = "locked_amount_paise", nullable = false)
    private long lockedAmountPaise;

    @Column(name = "locked_per_seat_paise", nullable = false)
    private long lockedPerSeatPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "pending_plan_id")
    private UUID pendingPlanId;

    @Column(name = "pending_seats")
    private Integer pendingSeats;

    @Column(name = "pending_change_at")
    private Instant pendingChangeAt;

    /** Earliest time dunning may retry an off-session charge (1/3/7 day backoff). */
    @Column(name = "next_dunning_retry_at")
    private Instant nextDunningRetryAt;

    /** Saved instrument token from the last successful checkout. */
    @Column(name = "razorpay_token_id", length = 80)
    private String razorpayTokenId;
}
