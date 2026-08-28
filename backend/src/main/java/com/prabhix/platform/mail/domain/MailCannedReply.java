package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "mail_canned_replies")
public class MailCannedReply extends TenantScopedEntity {

    @Column(name = "mailbox_id")
    private UUID mailboxId;

    @Column(name = "shortcut", length = 60)
    private String shortcut;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", nullable = false, columnDefinition = "jsonb")
    private String variables = "[]";

    @Column(name = "usage_count", nullable = false)
    private long usageCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
