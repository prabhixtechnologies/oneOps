-- =============================================================================
-- V36  Per-org AI settings and plan entitlement
-- =============================================================================

CREATE TABLE ai_org_settings (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                     bigint      NOT NULL DEFAULT 0,
    organization_id             uuid        NOT NULL,
    preferred_provider          varchar(32),
    preferred_chat_model        varchar(80),
    preferred_reasoning_model   varchar(80),
    -- Customer-facing AI first responder; off by default
    first_responder_enabled     boolean     NOT NULL DEFAULT false,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_by                  uuid,

    CONSTRAINT uq_ai_org_settings_org UNIQUE (organization_id),
    CONSTRAINT fk_ai_org_settings_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

-- Enable AI on all public plans (quota enforced separately via monthly-token-quota)
UPDATE billing_plans
SET entitlements = entitlements || '{"ai":true}'::jsonb
WHERE NOT (entitlements ? 'ai');
