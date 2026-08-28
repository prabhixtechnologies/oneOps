package com.prabhix.platform.billing.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "billing_orders")
public class BillingOrder extends TenantScopedEntity {

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "plan_id")
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private BillingEnums.OrderPurpose purpose;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "tax_paise", nullable = false)
    private long taxPaise;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BillingEnums.OrderStatus status = BillingEnums.OrderStatus.CREATED;

    @Column(name = "razorpay_order_id", length = 80)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 80)
    private String razorpayPaymentId;

    @Column(name = "receipt", nullable = false, length = 80, unique = true)
    private String receipt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> notes = Map.of();

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
