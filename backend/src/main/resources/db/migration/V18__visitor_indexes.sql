-- =============================================================================
-- V18  Visitor high-volume table indexes for retention and rollup queries
-- =============================================================================

-- Conversion funnel: visitors who identified with email.
ALTER TABLE visitors ADD COLUMN IF NOT EXISTS identified_at timestamptz;

UPDATE visitors SET identified_at = updated_at
WHERE email IS NOT NULL AND identified_at IS NULL;

-- Keyset pagination and retention pruning on page views.
CREATE INDEX ix_visitor_page_views_org_id_viewed
    ON visitor_page_views (organization_id, id, viewed_at DESC);

-- Keyset pagination and retention pruning on events.
CREATE INDEX ix_visitor_events_org_id_occurred
    ON visitor_events (organization_id, id, occurred_at DESC);

CREATE INDEX ix_visitors_org_identified
    ON visitors (organization_id, identified_at DESC NULLS LAST)
    WHERE email IS NOT NULL AND deleted_at IS NULL;
