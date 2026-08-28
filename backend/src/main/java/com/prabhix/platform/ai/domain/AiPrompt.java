package com.prabhix.platform.ai.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ai_prompts")
public class AiPrompt extends AuditableEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "task_key", nullable = false, length = 80)
    private String taskKey;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "template", nullable = false, columnDefinition = "text")
    private String template;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "model", length = 80)
    private String model;

    @Column(name = "temperature", nullable = false)
    private double temperature = 0.3;

    @Column(name = "prompt_version", nullable = false)
    private int promptVersion = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
