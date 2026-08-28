package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "mail_inbound_raw")
public class MailInboundRaw extends TenantScopedEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private MailEnums.InboundSource source;

    @Column(name = "source_uid")
    private Long sourceUid;

    @Column(name = "message_id_header", length = 998)
    private String messageIdHeader;

    @Column(name = "raw_file_id")
    private UUID rawFileId;

    @Column(name = "raw_content", columnDefinition = "text")
    private String rawContent;

    @Column(name = "size_bytes")
    private Integer sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MailEnums.InboundRawStatus status = MailEnums.InboundRawStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "resulting_message_id")
    private UUID resultingMessageId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
