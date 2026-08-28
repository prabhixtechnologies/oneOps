package com.prabhix.platform.billing.domain;

public final class BillingEnums {

    private BillingEnums() {
    }

    public enum PlanInterval {
        MONTHLY, ANNUAL, ONE_TIME, CUSTOM
    }

    public enum SubscriptionStatus {
        TRIALING, ACTIVE, PAST_DUE, PAUSED, CANCELLED, EXPIRED
    }

    public enum OrderPurpose {
        SUBSCRIPTION_NEW, SUBSCRIPTION_RENEWAL, SEAT_ADDITION, UPGRADE, ONE_TIME
    }

    public enum OrderStatus {
        CREATED, ATTEMPTED, CAPTURED, FAILED, REFUNDED, CANCELLED, EXPIRED
    }

    public enum PaymentStatus {
        CREATED, AUTHORIZED, CAPTURED, REFUNDED, FAILED
    }

    public enum InvoiceStatus {
        DRAFT, ISSUED, PAID, VOID, REFUNDED
    }

    public enum WebhookEventStatus {
        PENDING, PROCESSED, FAILED, IGNORED
    }

    public enum WebhookProvider {
        RAZORPAY
    }
}
