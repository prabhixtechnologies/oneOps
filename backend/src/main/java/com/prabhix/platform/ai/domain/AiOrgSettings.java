package com.prabhix.platform.ai.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_org_settings")
public class AiOrgSettings extends TenantScopedEntity {

    @Column(name = "preferred_provider", length = 32)
    private String preferredProvider;

    @Column(name = "preferred_chat_model", length = 80)
    private String preferredChatModel;

    @Column(name = "preferred_reasoning_model", length = 80)
    private String preferredReasoningModel;

    @Column(name = "first_responder_enabled", nullable = false)
    private boolean firstResponderEnabled;
}
