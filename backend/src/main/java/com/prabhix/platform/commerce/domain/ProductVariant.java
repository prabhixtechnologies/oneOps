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
@Table(name = "commerce_product_variants")
public class ProductVariant extends TenantScopedEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "compare_at_price_paise")
    private Long compareAtPricePaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "track_inventory", nullable = false)
    private boolean trackInventory;

    @Column(name = "stock_on_hand", nullable = false)
    private int stockOnHand;

    @Column(name = "stock_reserved", nullable = false)
    private int stockReserved;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 16)
    private CommerceEnums.BillingInterval billingInterval;

    @Column(name = "download_file_id")
    private UUID downloadFileId;

    @Column(name = "license_terms", columnDefinition = "text")
    private String licenseTerms;

    @Column(name = "service_duration_days")
    private Integer serviceDurationDays;

    @Column(name = "delivery_sla_days")
    private Integer deliverySlaDays;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
