package com.prabhix.platform.ai.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ai_usage")
public class AiUsage extends TenantScopedEntity {

    @Column(name = "feature", nullable = false, length = 80)
    private String feature;

    @Column(name = "task_key", length = 80)
    private String taskKey;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private Outcome outcome;

    @Column(name = "cost_estimate_paise", nullable = false)
    private long costEstimatePaise;

    @Column(name = "pii_redacted", nullable = false)
    private boolean piiRedacted;

    @Column(name = "correlation_type", length = 40)
    private String correlationType;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    public enum Outcome {
        SUCCESS, BLOCKED, ERROR, DISABLED
    }
}
