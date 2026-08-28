package com.prabhix.platform.commerce.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "commerce_carts")
public class Cart extends TenantScopedEntity {

    @Column(name = "cart_token", nullable = false, length = 64, updatable = false)
    private String cartToken;

    @Column(name = "visitor_id")
    private UUID visitorId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "discount_minor", nullable = false)
    private long discountMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "shipping_minor", nullable = false)
    private long shippingMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "discount_code_id")
    private UUID discountCodeId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
