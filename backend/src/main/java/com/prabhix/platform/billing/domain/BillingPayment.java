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
@Table(name = "billing_payments")
public class BillingPayment extends TenantScopedEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "razorpay_payment_id", nullable = false, length = 80, unique = true)
    private String razorpayPaymentId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BillingEnums.PaymentStatus status;

    @Column(name = "method", length = 32)
    private String method;

    @Column(name = "method_detail", length = 120)
    private String methodDetail;

    @Column(name = "bank", length = 80)
    private String bank;

    @Column(name = "wallet", length = 80)
    private String wallet;

    @Column(name = "vpa", length = 120)
    private String vpa;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_description", length = 500)
    private String errorDescription;

    @Column(name = "fee_paise")
    private Long feePaise;

    @Column(name = "tax_paise")
    private Long taxPaise;

    @Column(name = "refunded_paise", nullable = false)
    private long refundedPaise;

    @Column(name = "captured_at")
    private Instant capturedAt;
}
