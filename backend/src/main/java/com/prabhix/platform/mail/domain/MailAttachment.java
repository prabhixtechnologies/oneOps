package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "mail_attachments")
public class MailAttachment extends TenantScopedEntity {

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "is_inline", nullable = false)
    private boolean isInline;

    @Column(name = "content_id", length = 255)
    private String contentId;
}
