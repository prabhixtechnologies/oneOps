package com.prabhix.platform.push.domain;

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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "push_outbox")
public class PushOutbox extends AuditableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "push_token_id", nullable = false)
    private UUID pushTokenId;

    @Column(name = "notification_type", nullable = false, length = 64)
    private String notificationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PushEnums.OutboxStatus status = PushEnums.OutboxStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 6;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt = Instant.now();

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claimed_by", length = 80)
    private String claimedBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "provider_used", length = 32)
    private String providerUsed;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;
}
