-- =============================================================================
-- V3  Seed the permission vocabulary and the six system roles
-- =============================================================================
-- Mirrors com.prabhix.platform.security.rbac.Permission and SystemRole.
-- PermissionSeedConsistencyTest fails the build if the two drift apart.
--
-- Every insert is ON CONFLICT DO UPDATE so this file stays idempotent and later
-- migrations can re-run it after adding a permission.
-- =============================================================================

INSERT INTO permissions (code, category, description, assignable) VALUES
    -- Organization
    ('ORG_READ',                'ORGANIZATION', 'View organization profile and settings', true),
    ('ORG_UPDATE',              'ORGANIZATION', 'Change organization name, branding, and preferences', true),
    ('ORG_DELETE',              'ORGANIZATION', 'Permanently delete the organization', true),
    ('ORG_MEMBER_READ',         'ORGANIZATION', 'View the member directory', true),
    ('ORG_MEMBER_INVITE',       'ORGANIZATION', 'Invite people to the organization', true),
    ('ORG_MEMBER_UPDATE',       'ORGANIZATION', 'Change a member''s role or teams', true),
    ('ORG_MEMBER_REMOVE',       'ORGANIZATION', 'Remove a member from the organization', true),
    ('ORG_ROLE_READ',           'ORGANIZATION', 'View roles and their permissions', true),
    ('ORG_ROLE_MANAGE',         'ORGANIZATION', 'Create, edit, and delete custom roles', true),
    ('ORG_TEAM_READ',           'ORGANIZATION', 'View teams', true),
    ('ORG_TEAM_MANAGE',         'ORGANIZATION', 'Create, edit, and delete teams', true),
    ('ORG_API_KEY_MANAGE',      'ORGANIZATION', 'Issue and revoke API keys', true),

    -- Mail
    ('MAIL_READ',               'MAIL', 'Read threads in inboxes you belong to', true),
    ('MAIL_READ_ALL',           'MAIL', 'Read threads in every inbox, including ones you do not belong to', true),
    ('MAIL_SEND',               'MAIL', 'Reply to and forward mail', true),
    ('MAIL_ASSIGN',             'MAIL', 'Assign threads to people or teams', true),
    ('MAIL_THREAD_UPDATE',      'MAIL', 'Change thread status, priority, and tags', true),
    ('MAIL_THREAD_DELETE',      'MAIL', 'Delete threads', true),
    ('MAIL_NOTE_WRITE',         'MAIL', 'Add internal notes to a thread', true),
    ('MAIL_MAILBOX_READ',       'MAIL', 'View shared inbox configuration', true),
    ('MAIL_MAILBOX_MANAGE',     'MAIL', 'Create and configure shared inboxes, routing rules, and SLA policies', true),
    ('MAIL_DOMAIN_READ',        'MAIL', 'View mail domains and their DNS status', true),
    ('MAIL_DOMAIN_MANAGE',      'MAIL', 'Add, verify, and remove mail domains', true),
    ('MAIL_TEMPLATE_READ',      'MAIL', 'View transactional email templates', true),
    ('MAIL_TEMPLATE_MANAGE',    'MAIL', 'Create and edit transactional email templates', true),
    ('MAIL_SUPPRESSION_MANAGE', 'MAIL', 'View and clear the suppression list', true),

    -- Billing
    ('BILLING_READ',            'BILLING', 'View plan, usage, and invoices', true),
    ('BILLING_MANAGE',          'BILLING', 'Change plan, pay, and update billing details', true),
    ('BILLING_INVOICE_DOWNLOAD','BILLING', 'Download invoices', true),

    -- Files
    ('FILE_READ',               'FILES', 'Download files and attachments', true),
    ('FILE_UPLOAD',             'FILES', 'Upload files and attachments', true),
    ('FILE_DELETE',             'FILES', 'Delete files', true),

    -- Audit
    ('AUDIT_READ',              'AUDIT', 'View the audit log', true),

    -- Platform: assignable = false keeps this out of every customer role editor.
    ('PLATFORM_ADMIN',          'PLATFORM', 'Administer the whole platform across all organizations', false)
ON CONFLICT (code) DO UPDATE
    SET category    = EXCLUDED.category,
        description = EXCLUDED.description,
        assignable  = EXCLUDED.assignable;


-- -----------------------------------------------------------------------------
-- System roles. organization_id IS NULL means every tenant shares these rows.
-- -----------------------------------------------------------------------------
INSERT INTO roles (organization_id, role_key, name, description, is_system, rank) VALUES
    (NULL, 'OWNER',   'Owner',   'Complete control over the organization, its billing, and its data', true, 10),
    (NULL, 'ADMIN',   'Admin',   'Manages people, inboxes, and settings, but cannot delete the organization', true, 20),
    (NULL, 'MANAGER', 'Manager', 'Runs a team: full inbox oversight, assignment, and reporting', true, 30),
    (NULL, 'AGENT',   'Agent',   'Works the shared inboxes they belong to', true, 40),
    (NULL, 'MEMBER',  'Member',  'Standard access for everyone in the organization', true, 50),
    (NULL, 'VIEWER',  'Viewer',  'Read-only access', true, 60)
ON CONFLICT (role_key) WHERE organization_id IS NULL DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        rank        = EXCLUDED.rank;


-- -----------------------------------------------------------------------------
-- Role to permission grants.
-- -----------------------------------------------------------------------------

-- OWNER gets everything a customer may hold. Expressed as a query rather than a
-- list so a new assignable permission is granted to owners automatically.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.role_key = 'OWNER' AND r.organization_id IS NULL
  AND p.assignable = true
ON CONFLICT DO NOTHING;

-- The remaining roles are explicit: an unlisted permission must stay ungranted
-- even when a new one is introduced.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('ORG_READ'), ('ORG_UPDATE'),
    ('ORG_MEMBER_READ'), ('ORG_MEMBER_INVITE'), ('ORG_MEMBER_UPDATE'), ('ORG_MEMBER_REMOVE'),
    ('ORG_ROLE_READ'), ('ORG_ROLE_MANAGE'), ('ORG_TEAM_READ'), ('ORG_TEAM_MANAGE'),
    ('ORG_API_KEY_MANAGE'),
    ('MAIL_READ'), ('MAIL_READ_ALL'), ('MAIL_SEND'), ('MAIL_ASSIGN'),
    ('MAIL_THREAD_UPDATE'), ('MAIL_THREAD_DELETE'), ('MAIL_NOTE_WRITE'),
    ('MAIL_MAILBOX_READ'), ('MAIL_MAILBOX_MANAGE'),
    ('MAIL_DOMAIN_READ'), ('MAIL_DOMAIN_MANAGE'),
    ('MAIL_TEMPLATE_READ'), ('MAIL_TEMPLATE_MANAGE'), ('MAIL_SUPPRESSION_MANAGE'),
    ('BILLING_READ'), ('BILLING_MANAGE'), ('BILLING_INVOICE_DOWNLOAD'),
    ('FILE_READ'), ('FILE_UPLOAD'), ('FILE_DELETE'),
    ('AUDIT_READ')
) AS granted(code)
WHERE r.role_key = 'ADMIN' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('ORG_READ'), ('ORG_MEMBER_READ'), ('ORG_TEAM_READ'), ('ORG_TEAM_MANAGE'), ('ORG_ROLE_READ'),
    ('MAIL_READ'), ('MAIL_READ_ALL'), ('MAIL_SEND'), ('MAIL_ASSIGN'),
    ('MAIL_THREAD_UPDATE'), ('MAIL_NOTE_WRITE'),
    ('MAIL_MAILBOX_READ'), ('MAIL_MAILBOX_MANAGE'),
    ('MAIL_TEMPLATE_READ'), ('MAIL_TEMPLATE_MANAGE'), ('MAIL_DOMAIN_READ'),
    ('BILLING_READ'),
    ('FILE_READ'), ('FILE_UPLOAD'),
    ('AUDIT_READ')
) AS granted(code)
WHERE r.role_key = 'MANAGER' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

-- AGENT deliberately lacks MAIL_READ_ALL: an agent sees only the inboxes they
-- are a member of, which is what keeps HR mail away from support staff.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('ORG_READ'), ('ORG_MEMBER_READ'), ('ORG_TEAM_READ'),
    ('MAIL_READ'), ('MAIL_SEND'), ('MAIL_ASSIGN'), ('MAIL_THREAD_UPDATE'), ('MAIL_NOTE_WRITE'),
    ('MAIL_MAILBOX_READ'), ('MAIL_TEMPLATE_READ'),
    ('FILE_READ'), ('FILE_UPLOAD')
) AS granted(code)
WHERE r.role_key = 'AGENT' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('ORG_READ'), ('ORG_MEMBER_READ'), ('ORG_TEAM_READ'),
    ('MAIL_READ'), ('MAIL_MAILBOX_READ'),
    ('FILE_READ'), ('FILE_UPLOAD')
) AS granted(code)
WHERE r.role_key = 'MEMBER' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('ORG_READ'), ('ORG_MEMBER_READ'), ('ORG_TEAM_READ'), ('MAIL_READ'), ('FILE_READ')
) AS granted(code)
WHERE r.role_key = 'VIEWER' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;
