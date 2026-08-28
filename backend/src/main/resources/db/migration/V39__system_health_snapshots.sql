-- =============================================================================
-- V39  System health metric rollups for the observability dashboard
--
-- Hourly aggregates per organization (null org = platform-wide). Not tenant-auditable
-- entity rows — plain append-only snapshots with no soft-delete columns.
-- =============================================================================

CREATE TABLE system_health_snapshots (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid,
    bucket_start        timestamptz NOT NULL,
    bucket_granularity  varchar(8)  NOT NULL DEFAULT 'HOUR',
    error_count         bigint      NOT NULL DEFAULT 0,
    warn_count          bigint      NOT NULL DEFAULT 0,
    request_count       bigint      NOT NULL DEFAULT 0,
    slow_request_count  bigint      NOT NULL DEFAULT 0,
    mail_outbox_pending bigint      NOT NULL DEFAULT 0,
    mail_outbox_failed  bigint      NOT NULL DEFAULT 0,
    payment_failures    bigint      NOT NULL DEFAULT 0,
    ai_tokens_used      bigint      NOT NULL DEFAULT 0,
    chat_queue_wait_ms  bigint      NOT NULL DEFAULT 0,
    active_visitors     bigint      NOT NULL DEFAULT 0,
    top_event_codes     jsonb       NOT NULL DEFAULT '[]'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_health_snapshot UNIQUE (organization_id, bucket_start, bucket_granularity)
);

CREATE INDEX ix_health_snapshots_org_time
    ON system_health_snapshots (organization_id, bucket_start DESC);

COMMENT ON TABLE system_health_snapshots IS
    'Hourly operational metric rollups for the log viewer dashboard charts.';
