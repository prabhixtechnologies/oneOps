-- V51  SES/SNS feedback webhook events (idempotent SNS message processing).
--
-- Bounce and complaint notifications arrive through SNS, not the authenticated API.
-- Persisting them first matches the billing webhook pattern: signature is verified,
-- duplicates are ignored, and processing failures can be retried without losing data.

CREATE TABLE mail_webhook_events (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    provider            varchar(24) NOT NULL DEFAULT 'SES_SNS',
    provider_event_id   varchar(120),
    event_type          varchar(80) NOT NULL,
    payload             jsonb       NOT NULL,
    signature           varchar(512),
    signature_verified  boolean     NOT NULL DEFAULT false,
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts            integer     NOT NULL DEFAULT 0,
    last_error          varchar(2000),
    organization_id     uuid,
    received_at         timestamptz NOT NULL DEFAULT now(),
    processed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_mail_webhook_events_status
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'IGNORED')),
    CONSTRAINT ck_mail_webhook_events_attempts CHECK (attempts >= 0),
    CONSTRAINT fk_mail_webhook_events_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_mail_webhook_events_provider_event
    ON mail_webhook_events (provider, provider_event_id)
    WHERE provider_event_id IS NOT NULL;
CREATE INDEX ix_mail_webhook_events_pending ON mail_webhook_events (received_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_mail_webhook_events_org ON mail_webhook_events (organization_id, received_at DESC);

CREATE INDEX ix_mail_outbox_provider_message
    ON mail_outbox (provider_message_id)
    WHERE provider_message_id IS NOT NULL;
