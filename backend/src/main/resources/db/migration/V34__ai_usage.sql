-- =============================================================================
-- V34  AI usage accounting and configurable model cost rates
-- =============================================================================

CREATE TABLE ai_model_rates (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    provider            varchar(32) NOT NULL,
    model               varchar(80) NOT NULL,
    -- Cost per 1M tokens in INR paise (fractional stored as numeric)
    prompt_paise_per_million    numeric(14, 4) NOT NULL DEFAULT 0,
    completion_paise_per_million numeric(14, 4) NOT NULL DEFAULT 0,
    currency            varchar(3)  NOT NULL DEFAULT 'INR',
    effective_from      timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_ai_model_rates_provider_model UNIQUE (provider, model)
);

CREATE TABLE ai_usage (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    feature             varchar(80) NOT NULL,
    task_key            varchar(80),
    provider            varchar(32) NOT NULL,
    model               varchar(80) NOT NULL,
    prompt_tokens       integer     NOT NULL DEFAULT 0,
    completion_tokens   integer     NOT NULL DEFAULT 0,
    total_tokens        integer     NOT NULL DEFAULT 0,
    latency_ms          integer     NOT NULL DEFAULT 0,
    -- SUCCESS | BLOCKED | ERROR | DISABLED
    outcome             varchar(16) NOT NULL,
    cost_estimate_paise bigint      NOT NULL DEFAULT 0,
    pii_redacted        boolean     NOT NULL DEFAULT false,
    correlation_type    varchar(40),
    correlation_id      uuid,
    error_code          varchar(40),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_ai_usage_outcome CHECK (outcome IN ('SUCCESS', 'BLOCKED', 'ERROR', 'DISABLED')),
    CONSTRAINT ck_ai_usage_tokens CHECK (prompt_tokens >= 0 AND completion_tokens >= 0 AND total_tokens >= 0),
    CONSTRAINT fk_ai_usage_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX ix_ai_usage_org_created ON ai_usage (organization_id, created_at DESC, id DESC);
CREATE INDEX ix_ai_usage_org_month ON ai_usage (organization_id, created_at)
    WHERE outcome = 'SUCCESS';

-- Default cost rates (INR paise per 1M tokens). Configurable without redeploy.
INSERT INTO ai_model_rates (provider, model, prompt_paise_per_million, completion_paise_per_million) VALUES
('gemini', 'gemini-2.0-flash', 350, 1050),
('gemini', 'gemini-2.0-flash-thinking-exp', 700, 2100),
('gemini', 'text-embedding-004', 50, 0),
('openai', 'gpt-4o-mini', 1200, 4800),
('openai', 'gpt-4o', 20000, 80000),
('openai', 'text-embedding-3-small', 20, 0),
('anthropic', 'claude-3-5-haiku-latest', 800, 4000),
('anthropic', 'claude-3-5-sonnet-latest', 24000, 120000);
