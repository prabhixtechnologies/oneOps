-- =============================================================================
-- V17  Chat transcript email template
-- =============================================================================

INSERT INTO mail_templates
    (organization_id, template_key, locale, name, description, subject, body_html, variables, category)
VALUES
(NULL, 'chat.transcript', 'en', 'Chat transcript',
 'Sent to a visitor when a chat conversation is closed',
 'Your chat with [[${organizationName}]]',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:560px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Chat transcript</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 24px">Here is a copy of your recent conversation with us.</p>'
 '<div th:utext="${transcriptHtml}" style="font-size:14px;line-height:1.7;border-left:3px solid #7C3AED;padding-left:16px;margin:0 0 24px"></div>'
 '<p style="font-size:13px;color:#6b6b7b;margin:0">If you have further questions, reply to this email or start a new chat on our site.</p>'
 '</div>',
 '[{"name":"organizationName","required":true,"example":"Acme Corp"},'
 '{"name":"transcriptHtml","required":true,"example":"<p><strong>Agent:</strong> Hello!</p>"}]'::jsonb,
 'TRANSACTIONAL')
ON CONFLICT (template_key, locale) WHERE organization_id IS NULL DO NOTHING;

INSERT INTO mail_templates
    (organization_id, template_key, locale, name, description, subject, body_html, variables, category)
VALUES
(NULL, 'chat.offline-message', 'en', 'Offline chat message',
 'Sent to the support mailbox when no agent is available',
 'Offline chat from [[${visitorName}]]',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:560px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Offline chat message</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 8px"><strong>From:</strong> <span th:text="${visitorName}">Visitor</span> (<span th:text="${visitorEmail}">email</span>)</p>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 24px"><strong>Subject:</strong> <span th:text="${subject}">Subject</span></p>'
 '<div th:utext="${messageBody}" style="font-size:14px;line-height:1.7;border-left:3px solid #7C3AED;padding-left:16px"></div>'
 '</div>',
 '[{"name":"visitorName","required":true,"example":"Jane Doe"},'
 '{"name":"visitorEmail","required":true,"example":"jane@example.com"},'
 '{"name":"subject","required":false,"example":"Pricing question"},'
 '{"name":"messageBody","required":true,"example":"Hello, I need help with..."}]'::jsonb,
 'TRANSACTIONAL')
ON CONFLICT (template_key, locale) WHERE organization_id IS NULL DO NOTHING;
