package com.prabhix.platform.user.domain;

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

/**
 * This platform's copy of a person identity knows.
 *
 * <p>Keyed by identity's id, so the token's {@code sub} is the primary key here and nothing has to be
 * translated. Carries no credential: passwords, lockout and sign-in history live at identity, and the
 * columns that once held them here are gone or unmapped. What this row owns is what identity has no
 * concept of — the default organization, notification preferences, and platform staff authority.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "locale", nullable = false, length = 16)
    private String locale = "en-IN";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "platform_admin", nullable = false)
    private boolean platformAdmin;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "default_organization_id")
    private UUID defaultOrganizationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_prefs", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> notificationPrefs = Map.of();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String effectiveDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : fullName;
    }

    /** No LOCKED: lockout is identity's decision now, and a locked account cannot obtain a token. */
    public enum UserStatus {
        ACTIVE, INVITED, DISABLED
    }
}
