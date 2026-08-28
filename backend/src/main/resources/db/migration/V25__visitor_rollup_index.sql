-- =============================================================================
-- V25  Visitor rollup query support
-- =============================================================================

CREATE INDEX IF NOT EXISTS ix_visitor_daily_aggregates_org_metric_date
    ON visitor_daily_aggregates (organization_id, metric_type, aggregate_date DESC);

CREATE INDEX IF NOT EXISTS ix_visitor_sessions_org_started_date
    ON visitor_sessions (organization_id, started_at DESC);
