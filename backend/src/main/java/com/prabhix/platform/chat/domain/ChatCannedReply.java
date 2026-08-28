package com.prabhix.platform.chat.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "chat_canned_replies")
public class ChatCannedReply extends TenantScopedEntity {

    @Column(name = "shortcut", length = 60)
    private String shortcut;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "usage_count", nullable = false)
    private long usageCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
