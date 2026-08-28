package com.prabhix.platform.commerce.domain;

public final class CommerceEnums {

    private CommerceEnums() {
    }

    public enum ProductType {
        SUBSCRIPTION, DIGITAL, SERVICE, PHYSICAL
    }

    public enum ProductStatus {
        DRAFT, ACTIVE, ARCHIVED
    }

    public enum BillingInterval {
        MONTHLY, ANNUAL, ONE_TIME
    }

    public enum OrderStatus {
        PENDING_PAYMENT, PAID, FULFILLED, CANCELLED, REFUNDED, PAYMENT_FAILED
    }

    public enum AddressType {
        BILLING, SHIPPING
    }

    public enum PaymentStatus {
        INITIATED, CAPTURED, FAILED, REFUNDED
    }

    public enum InvoiceStatus {
        ISSUED, PAID, REFUNDED
    }

    public enum DiscountType {
        PERCENTAGE, FIXED_AMOUNT
    }

    public enum WebhookEventStatus {
        PENDING, PROCESSED, FAILED, IGNORED
    }

    public enum ShipmentStatus {
        PENDING_PICK, READY_TO_SHIP, SHIPPED, DELIVERED
    }

    public enum EngagementStatus {
        ACTIVE, COMPLETED, CANCELLED
    }

    public enum SubscriptionStatus {
        ACTIVE, PAST_DUE, CANCELLED, EXPIRED
    }
}
