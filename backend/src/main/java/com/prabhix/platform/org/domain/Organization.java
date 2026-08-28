package com.prabhix.platform.org.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
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

@Getter
@Setter
@Entity
@Table(name = "organizations")
public class Organization extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 80, unique = true)
    private String slug;

    @Column(name = "legal_name", length = 250)
    private String legalName;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "billing_email", columnDefinition = "citext")
    private String billingEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_address", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> billingAddress = Map.of();

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "locale", nullable = false, length = 16)
    private String locale = "en-IN";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrganizationStatus status = OrganizationStatus.TRIAL;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "seat_limit", nullable = false)
    private int seatLimit = 5;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settings = Map.of();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "purge_scheduled_at")
    private Instant purgeScheduledAt;

    public enum OrganizationStatus {
        ACTIVE, TRIAL, SUSPENDED, CANCELLED, DELETED
    }
}
