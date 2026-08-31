package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
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
@Table(schema = "mail", name = "mail_suppressions")
public class MailSuppression extends AuditableEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "address", nullable = false, columnDefinition = "citext")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 24)
    private MailEnums.SuppressionReason reason;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "bounce_count", nullable = false)
    private int bounceCount = 1;

    @Column(name = "last_bounce_at", nullable = false)
    private Instant lastBounceAt = Instant.now();
}
