-- =============================================================================
-- V32  Discount codes and order confirmation mail template
-- =============================================================================

CREATE TABLE commerce_discount_codes (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    code                    varchar(40) NOT NULL,
    description             varchar(300),
    -- PERCENTAGE | FIXED_AMOUNT
    discount_type           varchar(16) NOT NULL,
    percentage              integer,
    amount_minor            bigint,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    min_order_minor         bigint      NOT NULL DEFAULT 0,
    max_uses_total          integer,
    max_uses_per_customer   integer,
    uses_count              integer     NOT NULL DEFAULT 0,
    valid_from              timestamptz,
    valid_until             timestamptz,
    -- json array of product or category UUID strings; empty = all products
    product_ids             jsonb       NOT NULL DEFAULT '[]'::jsonb,
    category_ids            jsonb       NOT NULL DEFAULT '[]'::jsonb,
    active                  boolean     NOT NULL DEFAULT true,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_commerce_discount_codes_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_commerce_discount_codes_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT fk_commerce_discount_codes_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX ix_commerce_discount_codes_org_active
    ON commerce_discount_codes (organization_id, active)
    WHERE deleted_at IS NULL;


CREATE TABLE commerce_discount_redemptions (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         uuid        NOT NULL,
    discount_code_id        uuid        NOT NULL,
    order_id                uuid,
    cart_id                 uuid,
    customer_id             uuid,
    amount_minor            bigint      NOT NULL,
    redeemed_at             timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_commerce_discount_redemptions_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_discount_redemptions_code
        FOREIGN KEY (discount_code_id) REFERENCES commerce_discount_codes (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_discount_redemptions_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE SET NULL,
    CONSTRAINT fk_commerce_discount_redemptions_cart
        FOREIGN KEY (cart_id) REFERENCES commerce_carts (id) ON DELETE SET NULL,
    CONSTRAINT fk_commerce_discount_redemptions_customer
        FOREIGN KEY (customer_id) REFERENCES commerce_customers (id) ON DELETE SET NULL
);

CREATE INDEX ix_commerce_discount_redemptions_code ON commerce_discount_redemptions (discount_code_id);


ALTER TABLE commerce_carts
    ADD CONSTRAINT fk_commerce_carts_discount
        FOREIGN KEY (discount_code_id) REFERENCES commerce_discount_codes (id) ON DELETE SET NULL;

ALTER TABLE commerce_orders
    ADD CONSTRAINT fk_commerce_orders_discount
        FOREIGN KEY (discount_code_id) REFERENCES commerce_discount_codes (id) ON DELETE SET NULL;


INSERT INTO mail_templates
    (organization_id, template_key, locale, name, description, subject, body_html, variables, category)
VALUES
(NULL, 'commerce.order-confirmation', 'en', 'Order confirmation',
 'Sent to a storefront customer when payment succeeds',
 'Your order [[${orderNumber}]] from [[${organizationName}]]',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:560px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Thank you for your order</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 8px">Order <strong th:text="${orderNumber}">ORD-001</strong></p>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 24px">Total: <strong th:text="${orderTotal}">₹0.00</strong></p>'
 '<p style="font-size:14px;line-height:1.7;margin:0 0 24px">You can view your order anytime using the link below.</p>'
 '<p style="margin:0 0 24px"><a th:href="${orderUrl}" style="color:#7C3AED">View order</a></p>'
 '<p style="font-size:13px;color:#6b6b7b;margin:0">Questions? Reply to this email.</p>'
 '</div>',
 '[{"name":"organizationName","required":true,"example":"Acme Corp"},'
 '{"name":"orderNumber","required":true,"example":"ORD/2026-27/000001"},'
 '{"name":"orderTotal","required":true,"example":"₹1,180.00"},'
 '{"name":"orderUrl","required":true,"example":"https://example.com/orders/abc"}]'::jsonb,
 'TRANSACTIONAL')
ON CONFLICT (template_key, locale) WHERE organization_id IS NULL DO NOTHING;
