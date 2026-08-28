-- Post-payment provisioning for physical, service, and subscription catalog products.

ALTER TABLE commerce_orders
    ADD COLUMN IF NOT EXISTS subscription_checkout boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS renewal_subscription_id uuid;

CREATE TABLE commerce_order_shipments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    order_id            uuid        NOT NULL,
    order_item_id       uuid        NOT NULL,
    -- PENDING_PICK | READY_TO_SHIP | SHIPPED | DELIVERED
    status              varchar(24) NOT NULL DEFAULT 'PENDING_PICK',
    -- Carrier name is free text until a carrier integration is plugged in.
    carrier             varchar(80),
    tracking_number     varchar(120),
    shipped_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_commerce_shipments_order_item UNIQUE (order_item_id),
    CONSTRAINT ck_commerce_shipments_status CHECK (status IN
        ('PENDING_PICK', 'READY_TO_SHIP', 'SHIPPED', 'DELIVERED')),
    CONSTRAINT fk_commerce_shipments_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_shipments_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_shipments_order_item
        FOREIGN KEY (order_item_id) REFERENCES commerce_order_items (id) ON DELETE CASCADE
);

CREATE INDEX ix_commerce_shipments_org_status
    ON commerce_order_shipments (organization_id, status, created_at DESC);

CREATE TABLE commerce_service_engagements (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    order_id            uuid        NOT NULL,
    order_item_id       uuid        NOT NULL,
    duration_days       integer,
    delivery_sla_days   integer,
    -- ACTIVE | COMPLETED | CANCELLED
    status              varchar(16) NOT NULL DEFAULT 'ACTIVE',
    starts_at           timestamptz NOT NULL DEFAULT now(),
    ends_at             timestamptz,
    sla_due_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_commerce_engagements_order_item UNIQUE (order_item_id),
    CONSTRAINT ck_commerce_engagements_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_commerce_engagements_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_engagements_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_engagements_order_item
        FOREIGN KEY (order_item_id) REFERENCES commerce_order_items (id) ON DELETE CASCADE
);

CREATE INDEX ix_commerce_engagements_org_status
    ON commerce_service_engagements (organization_id, status, sla_due_at);

CREATE TABLE commerce_subscriptions (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    customer_id             uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    order_item_id           uuid        NOT NULL,
    product_id              uuid        NOT NULL,
    variant_id              uuid        NOT NULL,
    -- ACTIVE | PAST_DUE | CANCELLED | EXPIRED
    status                  varchar(16) NOT NULL DEFAULT 'ACTIVE',
    billing_interval        varchar(16) NOT NULL,
    current_period_start    timestamptz NOT NULL DEFAULT now(),
    current_period_end      timestamptz NOT NULL,
    next_billing_at         timestamptz,
    locked_price_minor      bigint      NOT NULL,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    razorpay_token_id       varchar(80),
    failed_payment_count    integer     NOT NULL DEFAULT 0,
    next_dunning_retry_at   timestamptz,
    cancelled_at            timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_subscriptions_order_item UNIQUE (order_item_id),
    CONSTRAINT ck_commerce_subscriptions_status CHECK (status IN
        ('ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_commerce_subscriptions_interval CHECK (billing_interval IN
        ('MONTHLY', 'ANNUAL', 'ONE_TIME')),
    CONSTRAINT ck_commerce_subscriptions_failed_count CHECK (failed_payment_count >= 0),
    CONSTRAINT fk_commerce_subscriptions_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_subscriptions_customer
        FOREIGN KEY (customer_id) REFERENCES commerce_customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commerce_subscriptions_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commerce_subscriptions_order_item
        FOREIGN KEY (order_item_id) REFERENCES commerce_order_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commerce_subscriptions_product
        FOREIGN KEY (product_id) REFERENCES commerce_products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commerce_subscriptions_variant
        FOREIGN KEY (variant_id) REFERENCES commerce_product_variants (id) ON DELETE RESTRICT
);

CREATE INDEX ix_commerce_subscriptions_renewal
    ON commerce_subscriptions (next_billing_at)
    WHERE status IN ('ACTIVE', 'PAST_DUE');

ALTER TABLE commerce_orders
    ADD CONSTRAINT fk_commerce_orders_renewal_subscription
        FOREIGN KEY (renewal_subscription_id) REFERENCES commerce_subscriptions (id) ON DELETE SET NULL;
