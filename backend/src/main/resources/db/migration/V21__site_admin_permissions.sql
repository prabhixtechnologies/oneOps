-- =============================================================================
-- V21  Site pipeline permissions and lead interest preservation
-- =============================================================================

INSERT INTO permissions (code, category, description, assignable) VALUES
    ('SITE_LEAD_READ',        'SITE', 'View marketing site leads', false),
    ('SITE_LEAD_MANAGE',      'SITE', 'Update lead status and notes', false),
    ('SITE_SUBSCRIBER_READ',  'SITE', 'View newsletter subscribers', false),
    ('SITE_APPLICATION_READ', 'SITE', 'View job applications', false),
    ('SITE_APPLICATION_MANAGE','SITE', 'Update application status', false)
ON CONFLICT (code) DO UPDATE
    SET category    = EXCLUDED.category,
        description = EXCLUDED.description,
        assignable  = EXCLUDED.assignable;

ALTER TABLE site_leads
    ADD COLUMN IF NOT EXISTS interest_raw varchar(120);

COMMENT ON COLUMN site_leads.interest_raw IS
    'Original interest string from the public form when it did not match a canonical enum name.';
