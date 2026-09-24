package com.prabhix.platform.commerce.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "commerce_discount_redemptions")
public class DiscountRedemption {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "discount_code_id", nullable = false)
    private UUID discountCodeId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "cart_id")
    private UUID cartId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();
}
