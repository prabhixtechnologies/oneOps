package com.prabhix.platform.flags.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "feature_flag_overrides")
public class FeatureFlagOverride extends AuditableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "flag_key", nullable = false, length = 80)
    private String flagKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "reason", length = 255)
    private String reason;
}
