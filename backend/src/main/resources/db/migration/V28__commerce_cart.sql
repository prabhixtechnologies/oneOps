-- =============================================================================
-- V28  Guest carts and storefront customers
-- =============================================================================

CREATE TABLE commerce_customers (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    email                   citext      NOT NULL,
    name                    varchar(160),
    phone                   varchar(32),
    marketing_consent       boolean     NOT NULL DEFAULT false,
    visitor_id              uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_customers_org_email UNIQUE (organization_id, email),
    CONSTRAINT fk_commerce_customers_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_customers_visitor
        FOREIGN KEY (visitor_id) REFERENCES visitors (id) ON DELETE SET NULL
);

CREATE INDEX ix_commerce_customers_org ON commerce_customers (organization_id, created_at DESC);
CREATE INDEX ix_commerce_customers_visitor ON commerce_customers (visitor_id) WHERE visitor_id IS NOT NULL;


CREATE TABLE commerce_carts (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    cart_token              varchar(64) NOT NULL,
    visitor_id              uuid,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    subtotal_minor          bigint      NOT NULL DEFAULT 0,
    discount_minor          bigint      NOT NULL DEFAULT 0,
    tax_minor               bigint      NOT NULL DEFAULT 0,
    shipping_minor          bigint      NOT NULL DEFAULT 0,
    total_minor             bigint      NOT NULL DEFAULT 0,
    discount_code_id        uuid,
    expires_at              timestamptz NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_carts_token UNIQUE (cart_token),
    CONSTRAINT ck_commerce_carts_amounts CHECK (
        subtotal_minor >= 0 AND discount_minor >= 0 AND tax_minor >= 0
        AND shipping_minor >= 0 AND total_minor >= 0),
    CONSTRAINT fk_commerce_carts_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_carts_visitor
        FOREIGN KEY (visitor_id) REFERENCES visitors (id) ON DELETE SET NULL
);

CREATE INDEX ix_commerce_carts_org_expires ON commerce_carts (organization_id, expires_at);
CREATE INDEX ix_commerce_carts_visitor ON commerce_carts (visitor_id) WHERE visitor_id IS NOT NULL;


CREATE TABLE commerce_cart_items (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    cart_id                 uuid        NOT NULL,
    variant_id              uuid        NOT NULL,
    quantity                integer     NOT NULL,
    unit_price_minor        bigint      NOT NULL,
    line_total_minor        bigint      NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_cart_items_cart_variant UNIQUE (cart_id, variant_id),
    CONSTRAINT ck_commerce_cart_items_qty CHECK (quantity > 0),
    CONSTRAINT ck_commerce_cart_items_amounts CHECK (unit_price_minor >= 0 AND line_total_minor >= 0),
    CONSTRAINT fk_commerce_cart_items_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_cart_items_cart
        FOREIGN KEY (cart_id) REFERENCES commerce_carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_cart_items_variant
        FOREIGN KEY (variant_id) REFERENCES commerce_product_variants (id) ON DELETE RESTRICT
);

CREATE INDEX ix_commerce_cart_items_cart ON commerce_cart_items (cart_id);
