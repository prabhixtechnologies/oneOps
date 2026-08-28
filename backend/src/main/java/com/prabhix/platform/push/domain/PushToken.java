package com.prabhix.platform.push.domain;

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
@Table(name = "push_tokens")
public class PushToken extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 8)
    private PushEnums.Platform platform;

    @Column(name = "token", nullable = false, length = 512)
    private String token;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(name = "device_name", length = 160)
    private String deviceName;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
