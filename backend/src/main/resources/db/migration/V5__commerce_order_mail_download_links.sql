-- Order confirmation mail now points at the marketing claim/download pages so
-- capability tokens are consumed server-side instead of being handed to JS.
-- Templates live in schema mail after V4.
UPDATE mail.mail_templates
SET body_html = '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:560px;margin:0 auto;padding:32px 24px;color:#0B0B12"><h1 style="font-size:20px;margin:0 0 16px">Thank you for your order</h1><p style="font-size:15px;line-height:1.6;margin:0 0 8px">Order <strong th:text="${orderNumber}">ORD-001</strong></p><p style="font-size:15px;line-height:1.6;margin:0 0 24px">Total: <strong th:text="${orderTotal}">₹0.00</strong></p><p style="font-size:14px;line-height:1.7;margin:0 0 24px">You can view your order anytime using the link below.</p><p style="margin:0 0 24px"><a th:href="${orderUrl}" style="color:#0e7490">View order</a></p><div th:utext="${downloadSection}"></div><p style="font-size:13px;color:#6b6b7b;margin:0">Questions? Reply to this email.</p></div>',
    variables = '[{"name": "organizationName", "example": "Acme Corp", "required": true}, {"name": "orderNumber", "example": "ORD/2026-27/000001", "required": true}, {"name": "orderTotal", "example": "₹1,180.00", "required": true}, {"name": "orderUrl", "example": "https://example.com/shop/order/claim/abc", "required": true}, {"name": "downloadSection", "example": "", "required": false}]'::jsonb,
    updated_at = now()
WHERE template_key = 'commerce.order-confirmation' AND organization_id IS NULL;
