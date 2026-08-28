package com.prabhix.platform.commerce.domain;

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
@Table(name = "commerce_orders")
public class CommerceOrder extends TenantScopedEntity {

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CommerceEnums.OrderStatus status = CommerceEnums.OrderStatus.PENDING_PAYMENT;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "cart_id")
    private UUID cartId;

    @Column(name = "access_token", nullable = false, length = 64, updatable = false)
    private String accessToken;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "discount_minor", nullable = false)
    private long discountMinor;

    @Column(name = "cgst_minor", nullable = false)
    private long cgstMinor;

    @Column(name = "sgst_minor", nullable = false)
    private long sgstMinor;

    @Column(name = "igst_minor", nullable = false)
    private long igstMinor;

    @Column(name = "shipping_minor", nullable = false)
    private long shippingMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "discount_code_id")
    private UUID discountCodeId;

    @Column(name = "buyer_state", length = 80)
    private String buyerState;

    @Column(name = "seller_state", nullable = false, length = 80)
    private String sellerState = "Karnataka";

    @Column(name = "gst_percent", nullable = false)
    private int gstPercent = 18;

    @Column(name = "razorpay_order_id", length = 80)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 80)
    private String razorpayPaymentId;

    @Column(name = "stock_hold_expires_at")
    private Instant stockHoldExpiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "internal_note", columnDefinition = "text")
    private String internalNote;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "subscription_checkout", nullable = false)
    private boolean subscriptionCheckout;

    @Column(name = "renewal_subscription_id")
    private UUID renewalSubscriptionId;
}
