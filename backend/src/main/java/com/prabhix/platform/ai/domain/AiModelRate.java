package com.prabhix.platform.ai.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ai_model_rates")
public class AiModelRate extends AuditableEntity {

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "prompt_paise_per_million", nullable = false)
    private BigDecimal promptPaisePerMillion = BigDecimal.ZERO;

    @Column(name = "completion_paise_per_million", nullable = false)
    private BigDecimal completionPaisePerMillion = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom = Instant.now();
}
