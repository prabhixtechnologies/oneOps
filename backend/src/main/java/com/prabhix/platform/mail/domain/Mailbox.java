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

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "mail_mailboxes")
public class Mailbox extends TenantScopedEntity {

    @Column(name = "mail_domain_id")
    private UUID mailDomainId;

    @Column(name = "address", nullable = false, columnDefinition = "citext")
    private String address;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private MailEnums.MailboxKind kind = MailEnums.MailboxKind.SHARED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MailEnums.MailboxStatus status = MailEnums.MailboxStatus.ACTIVE;

    @Column(name = "colour", length = 9)
    private String colour;

    /**
     * Whose mailbox this is, for {@link MailEnums.MailboxKind#PERSONAL}. Null on a shared or system
     * mailbox, and null on a personal one whose membership was ambiguous when the column was added.
     *
     * <p>Access is still decided by {@code mail_mailbox_members}; this only answers "which of these is
     * mine", which previously required counting members and guessing.
     */
    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "signature_html", columnDefinition = "text")
    private String signatureHtml;

    @Column(name = "reply_to", columnDefinition = "citext")
    private String replyTo;

    @Column(name = "imap_host", length = 255)
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "imap_username", length = 255)
    private String imapUsername;

    @Column(name = "imap_password_enc", columnDefinition = "text")
    private String imapPasswordEnc;

    @Column(name = "imap_use_ssl", nullable = false)
    private boolean imapUseSsl = true;

    @Column(name = "imap_folder", nullable = false, length = 255)
    private String imapFolder = "INBOX";

    @Column(name = "imap_last_uid", nullable = false)
    private long imapLastUid;

    @Column(name = "imap_uid_validity")
    private Long imapUidValidity;

    @Column(name = "imap_last_polled_at")
    private Instant imapLastPolledAt;

    @Column(name = "imap_last_error", length = 500)
    private String imapLastError;

    @Column(name = "imap_consecutive_errors", nullable = false)
    private int imapConsecutiveErrors;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Column(name = "smtp_password_enc", columnDefinition = "text")
    private String smtpPasswordEnc;

    /**
     * BCrypt hash of the password a mail client authenticates with, which Dovecot's SQL passdb reads
     * as BLF-CRYPT.
     *
     * <p>Deliberately not {@link #imapPasswordEnc}: that is reversible AES-GCM ciphertext, because
     * the IMAP poller has to present the original password to someone else's server. This one is
     * one-way, and there is no code path that reads it back.
     */
    @Column(name = "password_hash", columnDefinition = "text")
    private String passwordHash;

    @Column(name = "password_updated_at")
    private Instant passwordUpdatedAt;

    @Column(name = "auto_reply_enabled", nullable = false)
    private boolean autoReplyEnabled;

    @Column(name = "auto_reply_subject", length = 255)
    private String autoReplySubject;

    @Column(name = "auto_reply_body_html", columnDefinition = "text")
    private String autoReplyBodyHtml;

    @Column(name = "sla_first_response_mins")
    private Integer slaFirstResponseMins;

    @Column(name = "sla_resolution_mins")
    private Integer slaResolutionMins;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", nullable = false, columnDefinition = "jsonb")
    private String businessHours = "{}";

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "open_thread_count", nullable = false)
    private int openThreadCount;

    @Column(name = "unassigned_count", nullable = false)
    private int unassignedCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
