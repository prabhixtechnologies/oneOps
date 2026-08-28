-- =============================================================================
-- V30  Commerce payments, webhooks, settings, invoices
-- =============================================================================

CREATE TABLE commerce_settings (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    seller_state            varchar(80) NOT NULL DEFAULT 'Karnataka',
    seller_name             varchar(200),
    seller_gstin            varchar(20),
    seller_address          text,
    order_number_prefix     varchar(10) NOT NULL DEFAULT 'ORD',
    gst_percent             integer     NOT NULL DEFAULT 18,
    flat_shipping_minor     bigint      NOT NULL DEFAULT 0,
    free_shipping_above_minor bigint,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_settings_org UNIQUE (organization_id),
    CONSTRAINT fk_commerce_settings_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);


CREATE TABLE commerce_payments (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    razorpay_payment_id     varchar(80),
    razorpay_order_id       varchar(80),
    -- INITIATED | CAPTURED | FAILED | REFUNDED
    status                  varchar(16) NOT NULL DEFAULT 'INITIATED',
    amount_minor            bigint      NOT NULL,
    refunded_minor          bigint      NOT NULL DEFAULT 0,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    method                  varchar(40),
    captured_at             timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT ck_commerce_payments_status CHECK (status IN ('INITIATED', 'CAPTURED', 'FAILED', 'REFUNDED')),
    CONSTRAINT fk_commerce_payments_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_payments_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_commerce_payments_razorpay_payment
    ON commerce_payments (razorpay_payment_id) WHERE razorpay_payment_id IS NOT NULL;
CREATE INDEX ix_commerce_payments_order ON commerce_payments (order_id);


CREATE TABLE commerce_webhook_events (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid,
    order_id                uuid,
    provider                varchar(16) NOT NULL DEFAULT 'RAZORPAY',
    provider_event_id       varchar(80),
    event_type              varchar(80) NOT NULL,
    payload                 jsonb       NOT NULL,
    signature               varchar(128),
    signature_verified      boolean     NOT NULL DEFAULT false,
    -- PENDING | PROCESSED | FAILED | IGNORED
    status                  varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts                integer     NOT NULL DEFAULT 0,
    last_error              text,
    received_at             timestamptz NOT NULL DEFAULT now(),
    processed_at            timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_commerce_webhook_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'IGNORED'))
);

CREATE UNIQUE INDEX uq_commerce_webhook_provider_event
    ON commerce_webhook_events (provider, provider_event_id)
    WHERE provider_event_id IS NOT NULL;


CREATE TABLE commerce_invoices (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    invoice_number          varchar(40) NOT NULL,
    financial_year          varchar(9)  NOT NULL,
    sequence_number         integer     NOT NULL,
    -- ISSUED | PAID | REFUNDED
    status                  varchar(16) NOT NULL DEFAULT 'ISSUED',
    issue_date              date        NOT NULL,
    bill_to_name            varchar(160) NOT NULL,
    bill_to_email           citext,
    bill_to_gstin           varchar(20),
    bill_to_address         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    line_items              jsonb       NOT NULL DEFAULT '[]'::jsonb,
    subtotal_minor          bigint      NOT NULL,
    discount_minor          bigint      NOT NULL DEFAULT 0,
    cgst_minor              bigint      NOT NULL DEFAULT 0,
    sgst_minor              bigint      NOT NULL DEFAULT 0,
    igst_minor              bigint      NOT NULL DEFAULT 0,
    total_minor             bigint      NOT NULL,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    place_of_supply         varchar(80) NOT NULL,
    pdf_file_id             uuid,
    paid_at                 timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_invoices_order UNIQUE (order_id),
    CONSTRAINT uq_commerce_invoices_org_number UNIQUE (organization_id, invoice_number),
    CONSTRAINT fk_commerce_invoices_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_invoices_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commerce_invoices_pdf
        FOREIGN KEY (pdf_file_id) REFERENCES stored_files (id) ON DELETE SET NULL
);

ALTER TABLE commerce_orders
    ADD CONSTRAINT fk_commerce_orders_invoice
        FOREIGN KEY (invoice_id) REFERENCES commerce_invoices (id) ON DELETE SET NULL;
