-- =============================================================================
-- V10  Newsletter double opt-in template
-- =============================================================================
-- SiteService publishes MailRequested with template key 'site.newsletter-confirm'
-- when someone subscribes from the marketing site. TemplateRenderer throws
-- MAIL_TEMPLATE_NOT_FOUND if the row is absent, so this seed is required rather
-- than cosmetic.
-- =============================================================================

INSERT INTO mail_templates
    (organization_id, template_key, locale, name, description, subject, body_html, variables, category)
VALUES
(NULL, 'site.newsletter-confirm', 'en', 'Newsletter confirmation',
 'Double opt-in confirmation for a marketing-site newsletter signup',
 'Confirm your Prabhix subscription',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">One more step</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 24px">Confirm this address to start receiving engineering notes and product updates from Prabhix Technologies. We send rarely, and you can leave at any time.</p>'
 '<p style="margin:0 0 28px"><a th:href="${confirmUrl}" href="#" style="background:#7C3AED;color:#fff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:15px;display:inline-block">Confirm subscription</a></p>'
 '<p style="font-size:13px;color:#6b6b7b;line-height:1.6;margin:0">If you did not sign up, ignore this email and nothing further will be sent. We only add an address once it has been confirmed here.</p>'
 '</div>',
 '[{"name":"confirmUrl","required":true,'
 '"example":"https://api.prabhixtechnologies.com/api/v1/site/subscribers/confirm?token=..."}]'::jsonb,
 'TRANSACTIONAL')
ON CONFLICT (template_key, locale) WHERE organization_id IS NULL DO NOTHING;
