package com.prabhix.platform.mail.domain;

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
@Table(schema = "mail", name = "mail_outbox")
public class MailOutbox extends AuditableEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "mailbox_id")
    private UUID mailboxId;

    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "template_key", length = 80)
    private String templateKey;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale = "en";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_variables", nullable = false, columnDefinition = "jsonb")
    private String templateVariables = "{}";

    @Column(name = "from_address", nullable = false, columnDefinition = "citext")
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(name = "reply_to", columnDefinition = "citext")
    private String replyTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_addresses", nullable = false, columnDefinition = "jsonb")
    private String toAddresses = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cc_addresses", nullable = false, columnDefinition = "jsonb")
    private String ccAddresses = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bcc_addresses", nullable = false, columnDefinition = "jsonb")
    private String bccAddresses = "[]";

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", nullable = false, columnDefinition = "jsonb")
    private String headers = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_ids", nullable = false, columnDefinition = "jsonb")
    private String attachmentIds = "[]";

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Column(name = "priority", nullable = false)
    private int priority = 50;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MailEnums.OutboxStatus status = MailEnums.OutboxStatus.PENDING;

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

    @Column(name = "transport_used", length = 32)
    private String transportUsed;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;
}
