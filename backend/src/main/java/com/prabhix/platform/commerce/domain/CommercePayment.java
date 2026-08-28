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
@Table(name = "commerce_payments")
public class CommercePayment extends TenantScopedEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "razorpay_payment_id", length = 80)
    private String razorpayPaymentId;

    @Column(name = "razorpay_order_id", length = 80)
    private String razorpayOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CommerceEnums.PaymentStatus status = CommerceEnums.PaymentStatus.INITIATED;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "refunded_minor", nullable = false)
    private long refundedMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "method", length = 40)
    private String method;

    @Column(name = "captured_at")
    private Instant capturedAt;
}
