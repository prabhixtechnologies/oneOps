-- =============================================================================
-- V37  Business event log (operations / observability)
--
-- Append-only, org-scoped operational events — distinct from audit_logs which
-- records compliance-relevant "who changed what". Written asynchronously; never
-- updated by application code.
--
-- Partitioned monthly like audit_logs for write scalability and future partition
-- detach. Retention is enforced by EventLogRetentionJob via bounded DELETE batches
-- (not instant partition drop) so prabhix.observability.event-log-retention can
-- be any duration, not only whole calendar months.
-- =============================================================================

CREATE TABLE event_logs (
    id               uuid        NOT NULL DEFAULT gen_random_uuid(),
    organization_id  uuid,
    event_code       varchar(80) NOT NULL,
    category         varchar(32) NOT NULL,
    severity         varchar(16) NOT NULL,
    correlation_id   varchar(64) NOT NULL,
    actor_user_id    uuid,
    actor_type       varchar(24) NOT NULL DEFAULT 'SYSTEM',
    actor_label      varchar(255),
    target_type      varchar(64),
    target_id        uuid,
    payload          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    ip_address       varchar(45),
    user_agent       varchar(500),
    security_event   boolean     NOT NULL DEFAULT false,
    contains_pii     boolean     NOT NULL DEFAULT false,
    occurred_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_event_logs PRIMARY KEY (id, occurred_at),
    CONSTRAINT ck_event_logs_severity
        CHECK (severity IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL')),
    CONSTRAINT ck_event_logs_actor_type
        CHECK (actor_type IN ('USER', 'API_KEY', 'SYSTEM', 'PLATFORM_ADMIN'))
) PARTITION BY RANGE (occurred_at);

CREATE INDEX ix_event_logs_org_listing
    ON event_logs (organization_id, occurred_at DESC, id DESC);
CREATE INDEX ix_event_logs_org_code
    ON event_logs (organization_id, event_code, occurred_at DESC);
CREATE INDEX ix_event_logs_correlation
    ON event_logs (correlation_id, occurred_at DESC);
CREATE INDEX ix_event_logs_target
    ON event_logs (organization_id, target_type, target_id, occurred_at DESC);
CREATE INDEX ix_event_logs_actor
    ON event_logs (organization_id, actor_user_id, occurred_at DESC);

CREATE TABLE event_logs_2026_08 PARTITION OF event_logs
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE event_logs_2026_09 PARTITION OF event_logs
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE event_logs_2026_10 PARTITION OF event_logs
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE event_logs_default PARTITION OF event_logs DEFAULT;

COMMENT ON TABLE event_logs IS
    'Append-only operational event log. Cross-reference audit_logs via correlation_id.';
