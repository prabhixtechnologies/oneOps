-- =============================================================================
-- V35  AI triage suggestions stored alongside deterministic routing
-- =============================================================================

CREATE TABLE mail_thread_ai_suggestions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    thread_id           uuid        NOT NULL,
    suggested_tags      jsonb       NOT NULL DEFAULT '[]'::jsonb,
    suggested_priority  varchar(16),
    intent              varchar(500),
    confidence          numeric(4, 3),
    provider            varchar(32),
    model               varchar(80),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_mail_thread_ai_suggestions_thread UNIQUE (thread_id),
    CONSTRAINT ck_mail_thread_ai_suggestions_priority
        CHECK (suggested_priority IS NULL OR suggested_priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT fk_mail_thread_ai_suggestions_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_thread_ai_suggestions_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE CASCADE
);

CREATE INDEX ix_mail_thread_ai_suggestions_org
    ON mail_thread_ai_suggestions (organization_id, created_at DESC);
