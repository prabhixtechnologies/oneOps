-- =============================================================================
-- V38  Event log search indexes
--
-- GIN on payload supports free-text filter (jsonb_path_ops is lighter but we need
-- containment + key existence; default jsonb_ops handles @> and text search via
-- cast). BRIN on occurred_at complements btree org listing for very large scans
-- where the planner can combine org_id equality + BRIN time filter.
--
-- At ~100k-user scale monthly partitions + btree indexes on org+correlation are
-- sufficient; BRIN is cheap insurance on the parent for cross-org platform-admin
-- time-range queries without a full seq scan.
-- =============================================================================

CREATE INDEX ix_event_logs_payload_gin ON event_logs USING gin (payload);
CREATE INDEX ix_event_logs_occurred_brin ON event_logs USING brin (occurred_at);
