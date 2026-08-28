package com.prabhix.platform.org.domain;

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
@Table(name = "organization_memberships")
public class OrganizationMembership extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "employee_id", length = 64)
    private String employeeId;

    @Column(name = "department", length = 120)
    private String department;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    public enum MembershipStatus {
        ACTIVE, PENDING, SUSPENDED, LEFT
    }
}
