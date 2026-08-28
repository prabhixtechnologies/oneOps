package com.prabhix.platform.chat.domain;

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
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chat_conversations")
public class ChatConversation extends TenantScopedEntity {

    @Column(name = "visitor_id")
    private UUID visitorId;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChatEnums.ConversationStatus status = ChatEnums.ConversationStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private ChatEnums.Priority priority = ChatEnums.Priority.NORMAL;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "visitor_name", length = 160)
    private String visitorName;

    @Column(name = "visitor_email", columnDefinition = "citext")
    private String visitorEmail;

    @Column(name = "assigned_agent_id")
    private UUID assignedAgentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    private List<String> tags = List.of();

    @Column(name = "unread_agent_count", nullable = false)
    private int unreadAgentCount;

    @Column(name = "unread_visitor_count", nullable = false)
    private int unreadVisitorCount;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
