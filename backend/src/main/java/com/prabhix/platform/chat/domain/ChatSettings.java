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

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chat_settings")
public class ChatSettings extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 16)
    private ChatEnums.Availability availability = ChatEnums.Availability.ONLINE;

    @Column(name = "away_message", length = 500)
    private String awayMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> businessHours = Map.of();

    @Column(name = "pre_chat_enabled", nullable = false)
    private boolean preChatEnabled = true;

    @Column(name = "offline_mailbox_id")
    private UUID offlineMailboxId;

    @Column(name = "transcript_template_key", nullable = false, length = 80)
    private String transcriptTemplateKey = "chat.transcript";

    @Column(name = "auto_assign_enabled", nullable = false)
    private boolean autoAssignEnabled = true;

    @Column(name = "max_concurrent_conversations", nullable = false)
    private int maxConcurrentConversations = 5;

    @Column(name = "routing_cursor", nullable = false)
    private int routingCursor;
}
