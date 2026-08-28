-- =============================================================================
-- V19  Align visitor_page_views / visitor_events with TenantScopedEntity
--
-- Both tables were created with only created_at, but their entities extend
-- TenantScopedEntity -> AuditableEntity, which maps four provenance columns.
-- Hibernate's schema validation refuses to start against the shortfall.
-- =============================================================================

ALTER TABLE visitor_page_views
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid;

ALTER TABLE visitor_events
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid;
