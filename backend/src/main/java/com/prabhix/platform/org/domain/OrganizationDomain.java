package com.prabhix.platform.org.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * A domain an organization claims, and may have proved it controls.
 *
 * <p>Verification gates everything. An unverified claim is worth nothing on purpose: without the DNS
 * check, one organization could claim {@code gmail.com} and auto-join every Gmail user, or claim a
 * competitor's domain to collect their staff as they sign up.
 */
@Getter
@Setter
@Entity
@Table(name = "organization_domains")
public class OrganizationDomain extends TenantScopedEntity {

    @Column(name = "domain", nullable = false, columnDefinition = "citext")
    private String domain;

    @Column(name = "verification_token", nullable = false, length = 64)
    private String verificationToken;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_check_error")
    private String lastCheckError;

    @Column(name = "auto_join_enabled", nullable = false)
    private boolean autoJoinEnabled;

    @Column(name = "auto_join_role_id")
    private UUID autoJoinRoleId;

    /**
     * Soft delete, so releasing a domain frees it to be claimed again.
     *
     * <p>The unique index on {@code domain} is partial on this column. A hard delete would work too,
     * but this keeps the audit trail of who claimed what — and a domain claim is exactly the kind of
     * record somebody eventually asks about.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Normalizes, because DNS is case-insensitive and the column is queried with bound parameters.
     *
     * <p>{@code citext} alone is not enough: pgjdbc binds a {@code String} as {@code varchar}, so a
     * parameterised comparison against a {@code citext} column does not fold case. Same defect that
     * made email lookup case-sensitive in the identity service.
     */
    public void setDomain(String domain) {
        this.domain = normalize(domain);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    /** Whether an address belongs to this domain. Subdomains do not count — {@code a.b.com} is not {@code b.com}. */
    public boolean covers(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        return at >= 0 && normalize(email.substring(at + 1)).equals(domain);
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        // A trailing dot is a valid fully-qualified domain in DNS and the same domain to everyone
        // else, so it is stripped rather than stored as a second distinct row.
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
