package com.prabhix.platform.commerce.domain;

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
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "commerce_discount_codes")
public class DiscountCode extends TenantScopedEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "description", length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private CommerceEnums.DiscountType discountType;

    @Column(name = "percentage")
    private Integer percentage;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "min_order_minor", nullable = false)
    private long minOrderMinor;

    @Column(name = "max_uses_total")
    private Integer maxUsesTotal;

    @Column(name = "max_uses_per_customer")
    private Integer maxUsesPerCustomer;

    @Column(name = "uses_count", nullable = false)
    private int usesCount;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> productIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> categoryIds = List.of();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
