package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_threads")
public class MailThread extends TenantScopedEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "reference_key", nullable = false, length = 24, unique = true)
    private String referenceKey;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "normalized_subject", nullable = false, length = 500)
    private String normalizedSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MailEnums.ThreadStatus status = MailEnums.ThreadStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private MailEnums.Priority priority = MailEnums.Priority.NORMAL;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "assignee_team_id")
    private UUID assigneeTeamId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "customer_email", columnDefinition = "citext")
    private String customerEmail;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participant_emails", nullable = false, columnDefinition = "jsonb")
    private String participantEmails = "[]";

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    @Column(name = "has_attachments", nullable = false)
    private boolean hasAttachments;

    @Column(name = "snippet", length = 320)
    private String snippet;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "last_message_direction", nullable = false, length = 16)
    private MailEnums.MessageDirection lastMessageDirection = MailEnums.MessageDirection.INBOUND;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "sla_policy_first_mins")
    private Integer slaPolicyFirstMins;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_breached_at")
    private Instant slaBreachedAt;

    @Column(name = "sla_paused_at")
    private Instant slaPausedAt;

    @Column(name = "sla_paused_ms", nullable = false)
    private long slaPausedMs;

    @Column(name = "spam_score", precision = 5, scale = 2)
    private BigDecimal spamScore;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
