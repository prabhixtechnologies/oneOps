package com.prabhix.platform.chat.domain;

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
@Table(name = "chat_messages")
public class ChatMessage extends TenantScopedEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 16)
    private ChatEnums.SenderType senderType;

    @Column(name = "sender_user_id")
    private UUID senderUserId;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
