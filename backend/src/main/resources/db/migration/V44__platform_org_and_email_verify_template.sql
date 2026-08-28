-- V44  Platform organization for user-global assets (avatars) and email verification template
--
-- Avatars are not tenant-owned: they follow the user across organizations. A dedicated
-- platform org holds those files so avatar upload never depends on an active org selection.

INSERT INTO organizations
    (id, name, slug, status, member_count, seat_limit)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'Prabhix Platform', 'prabhix-platform', 'ACTIVE', 0, 999);

INSERT INTO mail_templates
    (organization_id, template_key, locale, name, description, subject, body_html, variables, category)
VALUES
(NULL, 'auth.email-verify', 'en', 'Verify your email',
 'Email address verification link',
 'Verify your Prabhix email address',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Verify your email</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 24px">Hello <span th:text="${name}">there</span>, confirm this address belongs to you. The link works once and expires in <span th:text="${expiryMinutes}">15</span> minutes.</p>'
 '<p style="margin:0 0 28px"><a th:href="${link}" href="#" style="background:#7C3AED;color:#fff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:15px;display:inline-block">Verify email</a></p>'
 '<p style="font-size:13px;color:#6b6b7b;line-height:1.6;margin:0">If you did not request this, you can safely ignore this email.</p>'
 '</div>',
 '[{"name":"name","required":true,"example":"Priya"},'
 '{"name":"link","required":true,"example":"https://app.prabhixtechnologies.com/auth/verify-email?token=..."},'
 '{"name":"expiryMinutes","required":true,"example":"15"}]'::jsonb,
 'TRANSACTIONAL');
