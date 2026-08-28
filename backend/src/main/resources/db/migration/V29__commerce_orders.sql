-- =============================================================================
-- V29  Checkout orders, line items, addresses, status timeline
-- =============================================================================

CREATE TABLE commerce_order_counters (
    organization_id         uuid        NOT NULL,
    financial_year          varchar(9)  NOT NULL,
    last_sequence           integer     NOT NULL DEFAULT 0,
    updated_at              timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, financial_year),
    CONSTRAINT fk_commerce_order_counters_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);


CREATE TABLE commerce_orders (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_number            varchar(40) NOT NULL,
    financial_year          varchar(9)  NOT NULL,
    sequence_number         integer     NOT NULL,
    -- PENDING_PAYMENT | PAID | FULFILLED | CANCELLED | REFUNDED | PAYMENT_FAILED
    status                  varchar(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    customer_id             uuid,
    cart_id                 uuid,
    access_token            varchar(64) NOT NULL,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    subtotal_minor          bigint      NOT NULL DEFAULT 0,
    discount_minor          bigint      NOT NULL DEFAULT 0,
    cgst_minor              bigint      NOT NULL DEFAULT 0,
    sgst_minor              bigint      NOT NULL DEFAULT 0,
    igst_minor              bigint      NOT NULL DEFAULT 0,
    shipping_minor          bigint      NOT NULL DEFAULT 0,
    total_minor             bigint      NOT NULL DEFAULT 0,
    discount_code_id        uuid,
    buyer_state             varchar(80),
    seller_state            varchar(80) NOT NULL DEFAULT 'Karnataka',
    gst_percent             integer     NOT NULL DEFAULT 18,
    razorpay_order_id       varchar(80),
    razorpay_payment_id     varchar(80),
    stock_hold_expires_at   timestamptz,
    paid_at                 timestamptz,
    fulfilled_at            timestamptz,
    cancelled_at            timestamptz,
    internal_note           text,
    invoice_id              uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_orders_org_number UNIQUE (organization_id, order_number),
    CONSTRAINT uq_commerce_orders_access_token UNIQUE (access_token),
    CONSTRAINT ck_commerce_orders_status CHECK (status IN
        ('PENDING_PAYMENT', 'PAID', 'FULFILLED', 'CANCELLED', 'REFUNDED', 'PAYMENT_FAILED')),
    CONSTRAINT ck_commerce_orders_amounts CHECK (
        subtotal_minor >= 0 AND discount_minor >= 0 AND total_minor >= 0),
    CONSTRAINT fk_commerce_orders_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_orders_customer
        FOREIGN KEY (customer_id) REFERENCES commerce_customers (id) ON DELETE SET NULL,
    CONSTRAINT fk_commerce_orders_cart
        FOREIGN KEY (cart_id) REFERENCES commerce_carts (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_commerce_orders_razorpay
    ON commerce_orders (razorpay_order_id) WHERE razorpay_order_id IS NOT NULL;
CREATE INDEX ix_commerce_orders_org_status_created
    ON commerce_orders (organization_id, status, created_at DESC);
CREATE INDEX ix_commerce_orders_stock_hold
    ON commerce_orders (stock_hold_expires_at)
    WHERE status = 'PENDING_PAYMENT' AND stock_hold_expires_at IS NOT NULL;


CREATE TABLE commerce_order_items (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    product_id              uuid        NOT NULL,
    variant_id              uuid        NOT NULL,
    product_name            varchar(200) NOT NULL,
    variant_name            varchar(200) NOT NULL,
    sku                     varchar(80) NOT NULL,
    product_type            varchar(16) NOT NULL,
    quantity                integer     NOT NULL,
    unit_price_minor        bigint      NOT NULL,
    line_subtotal_minor     bigint      NOT NULL,
    hsn_code                varchar(20),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT ck_commerce_order_items_qty CHECK (quantity > 0),
    CONSTRAINT fk_commerce_order_items_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_order_items_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_order_items_product
        FOREIGN KEY (product_id) REFERENCES commerce_products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commerce_order_items_variant
        FOREIGN KEY (variant_id) REFERENCES commerce_product_variants (id) ON DELETE RESTRICT
);

CREATE INDEX ix_commerce_order_items_order ON commerce_order_items (order_id);


CREATE TABLE commerce_order_addresses (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    -- BILLING | SHIPPING
    address_type            varchar(16) NOT NULL,
    name                    varchar(160) NOT NULL,
    line1                   varchar(200) NOT NULL,
    line2                   varchar(200),
    city                    varchar(100) NOT NULL,
    state                   varchar(80) NOT NULL,
    pincode                 varchar(12) NOT NULL,
    country                 varchar(2)  NOT NULL DEFAULT 'IN',
    phone                   varchar(32),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT ck_commerce_order_addresses_type CHECK (address_type IN ('BILLING', 'SHIPPING')),
    CONSTRAINT fk_commerce_order_addresses_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_order_addresses_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE CASCADE
);

CREATE INDEX ix_commerce_order_addresses_order ON commerce_order_addresses (order_id);


CREATE TABLE commerce_order_events (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    event_type              varchar(40) NOT NULL,
    message                 varchar(500),
    metadata                jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_commerce_order_events_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_order_events_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE CASCADE
);

CREATE INDEX ix_commerce_order_events_order ON commerce_order_events (order_id, created_at ASC);
