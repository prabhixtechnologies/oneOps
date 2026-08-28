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
@Table(name = "commerce_product_media")
public class ProductMedia extends TenantScopedEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
