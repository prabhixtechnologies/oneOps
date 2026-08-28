package com.prabhix.platform.site.domain;

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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "site_leads")
public class SiteLead extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "employee_count", length = 32)
    private String employeeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest", nullable = false, length = 32)
    private SiteEnums.LeadInterest interest = SiteEnums.LeadInterest.OTHER;

    @Column(name = "interest_raw", length = 120)
    private String interestRaw;

    @Column(name = "message", nullable = false, length = 4000)
    private String message;

    @Column(name = "source", nullable = false, length = 80)
    private String source = "contact-form";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "utm", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> utm = Map.of();

    @Column(name = "referrer", length = 500)
    private String referrer;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SiteEnums.LeadStatus status = SiteEnums.LeadStatus.NEW;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "internal_notes", length = 4000)
    private String internalNotes;

    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "contacted_at")
    private Instant contactedAt;
}
