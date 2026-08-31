package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_thread_ai_suggestions")
public class MailThreadAiSuggestion extends TenantScopedEntity {

    @Column(name = "thread_id", nullable = false, unique = true)
    private UUID threadId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_tags", nullable = false, columnDefinition = "jsonb")
    private String suggestedTags = "[]";

    @Column(name = "suggested_priority", length = 16)
    private String suggestedPriority;

    @Column(name = "intent", length = 500)
    private String intent;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "model", length = 80)
    private String model;
}
