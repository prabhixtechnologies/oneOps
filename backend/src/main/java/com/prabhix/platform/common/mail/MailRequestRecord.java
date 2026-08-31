package com.prabhix.platform.common.mail;

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

/**
 * A durable "please send this email", written in the transaction of the work that asked for it.
 *
 * <p>Named for the row rather than the message, because {@link MailRequest} is the message. This is
 * the record of one, carrying the bookkeeping the relay needs to deliver it exactly once.
 */
@Getter
@Setter
@Entity
@Table(name = "mail_requests")
public class MailRequestRecord extends AuditableEntity {

    public enum Status { PENDING, CLAIMED, DELIVERED, FAILED, DEAD }

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "template_key", nullable = false, length = 80)
    private String templateKey;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale = "en";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_addresses", nullable = false, columnDefinition = "jsonb")
    private String toAddresses = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", nullable = false, columnDefinition = "jsonb")
    private String variables = "{}";

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Column(name = "priority", nullable = false)
    private int priority = MailRequest.PRIORITY_NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 6;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claimed_by", length = 80)
    private String claimedBy;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "outbox_id")
    private UUID outboxId;

    @Column(name = "last_error", length = 2000)
    private String lastError;
}
