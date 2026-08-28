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
@Table(name = "mail_messages")
public class MailMessage extends TenantScopedEntity {

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private MailEnums.MessageDirection direction;

    @Column(name = "message_id_header", length = 998)
    private String messageIdHeader;

    @Column(name = "in_reply_to", length = 998)
    private String inReplyTo;

    @Column(name = "references_header", columnDefinition = "text")
    private String referencesHeader;

    @Column(name = "from_address", nullable = false, columnDefinition = "citext")
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_addresses", nullable = false, columnDefinition = "jsonb")
    private String toAddresses = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cc_addresses", nullable = false, columnDefinition = "jsonb")
    private String ccAddresses = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bcc_addresses", nullable = false, columnDefinition = "jsonb")
    private String bccAddresses = "[]";

    @Column(name = "reply_to_address", columnDefinition = "citext")
    private String replyToAddress;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "snippet", length = 320)
    private String snippet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", nullable = false, columnDefinition = "jsonb")
    private String headers = "{}";

    @Column(name = "raw_file_id")
    private UUID rawFileId;

    @Column(name = "size_bytes")
    private Integer sizeBytes;

    @Column(name = "attachment_count", nullable = false)
    private int attachmentCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 16)
    private MailEnums.DeliveryStatus deliveryStatus = MailEnums.DeliveryStatus.RECEIVED;

    @Column(name = "delivery_error", length = 1000)
    private String deliveryError;

    @Column(name = "spam_score", precision = 5, scale = 2)
    private BigDecimal spamScore;

    @Column(name = "spf_result", length = 16)
    private String spfResult;

    @Column(name = "dkim_result", length = 16)
    private String dkimResult;

    @Column(name = "dmarc_result", length = 16)
    private String dmarcResult;

    @Column(name = "sent_by_user_id")
    private UUID sentByUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
