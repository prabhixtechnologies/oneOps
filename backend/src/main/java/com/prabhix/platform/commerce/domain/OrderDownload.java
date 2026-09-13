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
@Table(name = "commerce_order_downloads")
public class OrderDownload extends TenantScopedEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "download_token", nullable = false, length = 64)
    private String downloadToken;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "max_download_count", nullable = false)
    private int maxDownloadCount;

    @Column(name = "link_expires_at", nullable = false)
    private Instant linkExpiresAt;
}
