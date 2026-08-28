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
@Table(name = "invitations")
public class Invitation extends TenantScopedEntity {

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "reminder_count", nullable = false)
    private int reminderCount;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public enum InvitationStatus {
        PENDING, ACCEPTED, REVOKED, EXPIRED
    }
}
