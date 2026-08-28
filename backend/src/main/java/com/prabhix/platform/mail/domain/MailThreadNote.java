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
@Table(name = "mail_thread_notes")
public class MailThreadNote extends TenantScopedEntity {

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mentioned_users", nullable = false, columnDefinition = "jsonb")
    private String mentionedUsers = "[]";

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
