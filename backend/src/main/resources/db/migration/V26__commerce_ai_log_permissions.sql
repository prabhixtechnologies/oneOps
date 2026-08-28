-- =============================================================================
-- V26  Permission vocabulary for commerce, AI, and observability
--
-- Registered ahead of the feature tables so the modules that follow can grant
-- them without each owning a slice of this shared table. SystemRole is the
-- source of truth for which role holds what; RolePermissionSynchronizer
-- reconciles existing organizations on the next boot.
-- =============================================================================

INSERT INTO permissions (code, category, description, assignable) VALUES
    ('COMMERCE_CATALOG_READ',    'COMMERCE', 'View products, variants, and prices', true),
    ('COMMERCE_CATALOG_MANAGE',  'COMMERCE', 'Create and edit products, variants, and prices', true),
    ('COMMERCE_ORDER_READ',      'COMMERCE', 'View customer orders', true),
    ('COMMERCE_ORDER_MANAGE',    'COMMERCE', 'Fulfil, cancel, and annotate orders', true),
    ('COMMERCE_ORDER_REFUND',    'COMMERCE', 'Refund orders', true),
    ('COMMERCE_CUSTOMER_READ',   'COMMERCE', 'View storefront customers', true),
    ('COMMERCE_DISCOUNT_MANAGE', 'COMMERCE', 'Create and edit discount codes', true),
    ('COMMERCE_SETTINGS_MANAGE', 'COMMERCE', 'Configure the storefront, tax, and shipping', true),
    ('AI_USE',                   'AI',       'Use AI assistance in mail, chat, and lead workflows', true),
    ('AI_CONFIGURE',             'AI',       'Choose AI providers, models, and prompts', true),
    ('AI_USAGE_READ',            'AI',       'View AI usage and cost', true),
    ('LOG_READ',                 'OBSERVABILITY', 'Search application and business logs', true),
    ('LOG_EXPORT',               'OBSERVABILITY', 'Export logs', true)
ON CONFLICT (code) DO UPDATE
    SET category    = EXCLUDED.category,
        description = EXCLUDED.description,
        assignable  = EXCLUDED.assignable;
