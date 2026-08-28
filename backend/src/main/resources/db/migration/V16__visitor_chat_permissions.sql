-- =============================================================================
-- V16  Visitor and chat permissions
-- =============================================================================

INSERT INTO permissions (code, category, description, assignable) VALUES
    ('VISITOR_READ',       'VISITOR', 'View live visitors and visitor profiles', true),
    ('VISITOR_ANALYTICS',  'VISITOR', 'View visitor analytics and aggregates', true),
    ('VISITOR_MANAGE',     'VISITOR', 'Delete visitor data and manage consent', true),
    ('CHAT_READ',          'CHAT', 'Read chat conversations assigned to you', true),
    ('CHAT_READ_ALL',      'CHAT', 'Read all chat conversations in the organization', true),
    ('CHAT_REPLY',         'CHAT', 'Send chat messages and internal notes', true),
    ('CHAT_ASSIGN',        'CHAT', 'Assign and transfer chat conversations', true),
    ('CHAT_MANAGE',        'CHAT', 'Configure chat settings and canned replies', true)
ON CONFLICT (code) DO UPDATE
    SET category    = EXCLUDED.category,
        description = EXCLUDED.description,
        assignable  = EXCLUDED.assignable;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('VISITOR_READ'), ('VISITOR_ANALYTICS'), ('VISITOR_MANAGE'),
    ('CHAT_READ'), ('CHAT_READ_ALL'), ('CHAT_REPLY'), ('CHAT_ASSIGN'), ('CHAT_MANAGE')
) AS granted(code)
WHERE r.role_key = 'ADMIN' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('VISITOR_READ'), ('VISITOR_ANALYTICS'),
    ('CHAT_READ'), ('CHAT_READ_ALL'), ('CHAT_REPLY'), ('CHAT_ASSIGN')
) AS granted(code)
WHERE r.role_key = 'MANAGER' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('VISITOR_READ'),
    ('CHAT_READ'), ('CHAT_REPLY'), ('CHAT_ASSIGN')
) AS granted(code)
WHERE r.role_key = 'AGENT' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, code
FROM roles r
CROSS JOIN (VALUES
    ('VISITOR_READ'), ('VISITOR_ANALYTICS'),
    ('CHAT_READ')
) AS granted(code)
WHERE r.role_key = 'VIEWER' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;
