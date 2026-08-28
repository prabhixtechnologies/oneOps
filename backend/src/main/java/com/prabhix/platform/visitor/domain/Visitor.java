package com.prabhix.platform.visitor.domain;

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
@Table(name = "visitors")
public class Visitor extends TenantScopedEntity {

    @Column(name = "external_key", nullable = false, length = 64)
    private String externalKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false, length = 16)
    private VisitorEnums.ConsentStatus consentStatus = VisitorEnums.ConsentStatus.FULL;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "identified_user_id")
    private UUID identifiedUserId;

    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "first_touch_utm", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> firstTouchUtm = Map.of();

    @Column(name = "first_touch_referrer", length = 500)
    private String firstTouchReferrer;

    @Column(name = "merged_into_id")
    private UUID mergedIntoId;

    @Column(name = "identified_at")
    private Instant identifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
