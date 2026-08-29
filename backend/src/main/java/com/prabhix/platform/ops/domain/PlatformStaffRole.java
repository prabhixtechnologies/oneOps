package com.prabhix.platform.ops.domain;

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

/**
 * One grant of one staff role to one person.
 *
 * <p>A row per grant rather than a set of columns, because the grant is an event: "who gave this person
 * the ability to revoke anyone's session, and when" is a question that will eventually be asked, and a
 * boolean column cannot answer it. Revocation sets {@code revokedAt} rather than deleting, for the same
 * reason.
 */
@Getter
@Setter
@Entity
@Table(name = "platform_staff_roles")
public class PlatformStaffRole extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 24)
    private StaffRole role;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    /** Null for the rows backfilled from {@code users.platform_admin}, which predate this table. */
    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "note")
    private String note;

    public boolean isLive() {
        return revokedAt == null;
    }
}
