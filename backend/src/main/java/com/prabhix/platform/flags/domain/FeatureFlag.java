package com.prabhix.platform.flags.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "feature_flags")
public class FeatureFlag extends AuditableEntity {

    @Column(name = "flag_key", nullable = false, length = 80, unique = true)
    private String flagKey;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled;
}
