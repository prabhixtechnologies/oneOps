-- =============================================================================
-- V22  Chat feature entitlement on every public plan
-- =============================================================================

UPDATE billing_plans
SET entitlements = entitlements || '{"chat":true}'::jsonb
WHERE NOT (entitlements ? 'chat');
