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

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_thread_drafts")
public class MailThreadDraft extends TenantScopedEntity {

    /**
     * The thread this is a reply to, or null for a new message that has never been sent — there is
     * nothing for a first draft to hang off, which is why this stopped being mandatory in V64.
     */
    @Column(name = "thread_id")
    private UUID threadId;

    /**
     * Which mailbox it will be sent from. Only meaningful when {@link #threadId} is null; a reply is
     * always sent from the mailbox that received the thread.
     */
    @Column(name = "mailbox_id")
    private UUID mailboxId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_mode", nullable = false, length = 16)
    private MailEnums.ReplyMode replyMode = MailEnums.ReplyMode.REPLY;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_ids", nullable = false, columnDefinition = "jsonb")
    private String attachmentIds = "[]";
}
