-- =============================================================================
-- V8  Billing: plans, subscriptions, orders, payments, invoices, webhooks
-- =============================================================================
-- Money is stored as bigint paise, never as float or numeric-with-rounding.
-- Razorpay works in the smallest currency unit, so this also avoids a conversion
-- at every gateway boundary.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- billing_plans: the public price list. Platform-wide, not tenant-scoped.
-- -----------------------------------------------------------------------------
CREATE TABLE billing_plans (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    plan_key                varchar(60) NOT NULL,
    name                    varchar(120) NOT NULL,
    description             varchar(500),
    -- MONTHLY | ANNUAL | ONE_TIME | CUSTOM
    interval_type           varchar(16) NOT NULL DEFAULT 'MONTHLY',
    -- Price excluding tax, in paise. 0 for a free tier.
    amount_paise            bigint      NOT NULL,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    -- Per-seat charge added on top of the base amount, in paise.
    per_seat_paise          bigint      NOT NULL DEFAULT 0,
    included_seats          integer     NOT NULL DEFAULT 5,
    max_seats               integer,
    trial_days              integer     NOT NULL DEFAULT 0,
    -- Feature entitlements resolved into feature flags and quota checks.
    entitlements            jsonb       NOT NULL DEFAULT '{}'::jsonb,
    -- Razorpay Plan id for subscription-based billing.
    razorpay_plan_id        varchar(80),
    is_public               boolean     NOT NULL DEFAULT true,
    -- Retained so existing subscribers keep their price after a price change.
    is_active               boolean     NOT NULL DEFAULT true,
    rank                    integer     NOT NULL DEFAULT 100,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_billing_plans_key UNIQUE (plan_key),
    CONSTRAINT ck_billing_plans_interval
        CHECK (interval_type IN ('MONTHLY', 'ANNUAL', 'ONE_TIME', 'CUSTOM')),
    CONSTRAINT ck_billing_plans_amounts
        CHECK (amount_paise >= 0 AND per_seat_paise >= 0 AND included_seats >= 0),
    CONSTRAINT ck_billing_plans_max_seats
        CHECK (max_seats IS NULL OR max_seats >= included_seats)
);

CREATE INDEX ix_billing_plans_public ON billing_plans (rank)
    WHERE is_public = true AND is_active = true;


-- -----------------------------------------------------------------------------
-- billing_subscriptions: one active subscription per organization.
-- -----------------------------------------------------------------------------
CREATE TABLE billing_subscriptions (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                     bigint      NOT NULL DEFAULT 0,
    organization_id             uuid        NOT NULL,
    plan_id                     uuid        NOT NULL,
    -- TRIALING | ACTIVE | PAST_DUE | PAUSED | CANCELLED | EXPIRED
    status                      varchar(24) NOT NULL DEFAULT 'TRIALING',
    -- Seats the customer is paying for, which may exceed current membership.
    seats                       integer     NOT NULL DEFAULT 5,
    current_period_start        timestamptz NOT NULL DEFAULT now(),
    current_period_end          timestamptz NOT NULL,
    trial_ends_at               timestamptz,
    cancel_at_period_end        boolean     NOT NULL DEFAULT false,
    cancelled_at                timestamptz,
    cancellation_reason         varchar(500),
    -- Service continues this long past a failed renewal before suspension.
    grace_period_ends_at        timestamptz,
    -- Dunning state for failed renewals.
    failed_payment_count        integer     NOT NULL DEFAULT 0,
    last_payment_at             timestamptz,
    next_billing_at             timestamptz,
    razorpay_subscription_id    varchar(80),
    razorpay_customer_id        varchar(80),
    -- Price captured at subscribe time, so a public price change does not
    -- silently re-price an existing customer.
    locked_amount_paise         bigint      NOT NULL,
    locked_per_seat_paise       bigint      NOT NULL DEFAULT 0,
    currency                    varchar(3)  NOT NULL DEFAULT 'INR',
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_by                  uuid,

    CONSTRAINT ck_subscriptions_status CHECK (status IN
        ('TRIALING', 'ACTIVE', 'PAST_DUE', 'PAUSED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_subscriptions_seats CHECK (seats > 0),
    CONSTRAINT ck_subscriptions_period CHECK (current_period_end > current_period_start),
    CONSTRAINT ck_subscriptions_failed_count CHECK (failed_payment_count >= 0),
    CONSTRAINT fk_subscriptions_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_plan
        FOREIGN KEY (plan_id) REFERENCES billing_plans (id) ON DELETE RESTRICT
);

-- One live subscription per organization. Cancelled rows are kept for history.
CREATE UNIQUE INDEX uq_subscriptions_active_org
    ON billing_subscriptions (organization_id)
    WHERE status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'PAUSED');
CREATE INDEX ix_subscriptions_org  ON billing_subscriptions (organization_id, created_at DESC);
CREATE INDEX ix_subscriptions_plan ON billing_subscriptions (plan_id);
-- Drives the renewal sweep.
CREATE INDEX ix_subscriptions_renewal ON billing_subscriptions (next_billing_at)
    WHERE status IN ('ACTIVE', 'PAST_DUE');
CREATE INDEX ix_subscriptions_razorpay
    ON billing_subscriptions (razorpay_subscription_id)
    WHERE razorpay_subscription_id IS NOT NULL;


-- -----------------------------------------------------------------------------
-- billing_orders: our record of an intent to charge, created before the gateway
-- is called. Amount is decided server-side and never read from the client.
-- -----------------------------------------------------------------------------
CREATE TABLE billing_orders (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    subscription_id         uuid,
    plan_id                 uuid,
    -- SUBSCRIPTION_NEW | SUBSCRIPTION_RENEWAL | SEAT_ADDITION | UPGRADE | ONE_TIME
    purpose                 varchar(32) NOT NULL,
    amount_paise            bigint      NOT NULL,
    tax_paise               bigint      NOT NULL DEFAULT 0,
    total_paise             bigint      NOT NULL,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    -- CREATED | ATTEMPTED | CAPTURED | FAILED | REFUNDED | CANCELLED | EXPIRED
    status                  varchar(16) NOT NULL DEFAULT 'CREATED',
    razorpay_order_id       varchar(80),
    razorpay_payment_id     varchar(80),
    -- Receipt string sent to Razorpay; also our idempotency handle.
    receipt                 varchar(80) NOT NULL,
    notes                   jsonb       NOT NULL DEFAULT '{}'::jsonb,
    failure_reason          varchar(500),
    captured_at             timestamptz,
    initiated_by            uuid,
    expires_at              timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_billing_orders_receipt UNIQUE (receipt),
    CONSTRAINT ck_billing_orders_purpose CHECK (purpose IN
        ('SUBSCRIPTION_NEW', 'SUBSCRIPTION_RENEWAL', 'SEAT_ADDITION', 'UPGRADE', 'ONE_TIME')),
    CONSTRAINT ck_billing_orders_status CHECK (status IN
        ('CREATED', 'ATTEMPTED', 'CAPTURED', 'FAILED', 'REFUNDED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_billing_orders_amounts
        CHECK (amount_paise >= 0 AND tax_paise >= 0
           AND total_paise = amount_paise + tax_paise),
    CONSTRAINT fk_billing_orders_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_orders_subscription
        FOREIGN KEY (subscription_id) REFERENCES billing_subscriptions (id) ON DELETE SET NULL,
    CONSTRAINT fk_billing_orders_plan
        FOREIGN KEY (plan_id) REFERENCES billing_plans (id) ON DELETE SET NULL,
    CONSTRAINT fk_billing_orders_initiator
        FOREIGN KEY (initiated_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_billing_orders_razorpay
    ON billing_orders (razorpay_order_id) WHERE razorpay_order_id IS NOT NULL;
CREATE INDEX ix_billing_orders_org    ON billing_orders (organization_id, created_at DESC);
CREATE INDEX ix_billing_orders_status ON billing_orders (status, created_at DESC);
CREATE INDEX ix_billing_orders_subscription ON billing_orders (subscription_id);
CREATE INDEX ix_billing_orders_plan   ON billing_orders (plan_id);
CREATE INDEX ix_billing_orders_initiator ON billing_orders (initiated_by);
CREATE INDEX ix_billing_orders_payment
    ON billing_orders (razorpay_payment_id) WHERE razorpay_payment_id IS NOT NULL;


-- -----------------------------------------------------------------------------
-- billing_payments: one row per gateway payment attempt, including failures.
-- -----------------------------------------------------------------------------
CREATE TABLE billing_payments (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    razorpay_payment_id     varchar(80) NOT NULL,
    amount_paise            bigint      NOT NULL,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    -- CREATED | AUTHORIZED | CAPTURED | REFUNDED | FAILED
    status                  varchar(16) NOT NULL,
    -- card | upi | netbanking | wallet | emi
    method                  varchar(32),
    -- Last four digits or a UPI handle. Never a full instrument number.
    method_detail           varchar(120),
    bank                    varchar(80),
    wallet                  varchar(80),
    vpa                     varchar(120),
    -- True once the return-path HMAC has been verified.
    signature_verified      boolean     NOT NULL DEFAULT false,
    error_code              varchar(80),
    error_description       varchar(500),
    fee_paise               bigint,
    tax_paise               bigint,
    refunded_paise          bigint      NOT NULL DEFAULT 0,
    captured_at             timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_billing_payments_razorpay UNIQUE (razorpay_payment_id),
    CONSTRAINT ck_billing_payments_status CHECK (status IN
        ('CREATED', 'AUTHORIZED', 'CAPTURED', 'REFUNDED', 'FAILED')),
    CONSTRAINT ck_billing_payments_amounts
        CHECK (amount_paise >= 0 AND refunded_paise >= 0 AND refunded_paise <= amount_paise),
    CONSTRAINT fk_billing_payments_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_payments_order
        FOREIGN KEY (order_id) REFERENCES billing_orders (id) ON DELETE RESTRICT
);

CREATE INDEX ix_billing_payments_order ON billing_payments (order_id);
CREATE INDEX ix_billing_payments_org   ON billing_payments (organization_id, created_at DESC);


-- -----------------------------------------------------------------------------
-- billing_invoices: GST-compliant, sequentially numbered per financial year.
-- -----------------------------------------------------------------------------
CREATE TABLE billing_invoices (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_id                uuid,
    subscription_id         uuid,
    -- Gapless per financial year, e.g. PBX/2026-27/000042. Indian GST rules
    -- require the series to be continuous, which is why the counter table below
    -- exists rather than a plain sequence per organization.
    invoice_number          varchar(60) NOT NULL,
    -- Indian financial year label, e.g. 2026-27
    financial_year          varchar(9)  NOT NULL,
    sequence_number         integer     NOT NULL,
    -- DRAFT | ISSUED | PAID | VOID | REFUNDED
    status                  varchar(16) NOT NULL DEFAULT 'ISSUED',
    issue_date              date        NOT NULL DEFAULT CURRENT_DATE,
    due_date                date,
    paid_at                 timestamptz,
    -- Buyer snapshot at issue time. An invoice must not change when the
    -- organization later edits its address.
    bill_to_name            varchar(250) NOT NULL,
    bill_to_address         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    bill_to_gstin           varchar(15),
    bill_to_email           citext,
    -- Line items: description, quantity, unitPaise, amountPaise.
    line_items              jsonb       NOT NULL DEFAULT '[]'::jsonb,
    subtotal_paise          bigint      NOT NULL,
    discount_paise          bigint      NOT NULL DEFAULT 0,
    -- CGST + SGST for intra-state, IGST for inter-state. Only one pair applies.
    cgst_paise              bigint      NOT NULL DEFAULT 0,
    sgst_paise              bigint      NOT NULL DEFAULT 0,
    igst_paise              bigint      NOT NULL DEFAULT 0,
    total_paise             bigint      NOT NULL,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    -- HSN/SAC code for software services.
    sac_code                varchar(12) NOT NULL DEFAULT '998314',
    place_of_supply         varchar(80),
    notes                   varchar(1000),
    pdf_file_id             uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_billing_invoices_number UNIQUE (invoice_number),
    CONSTRAINT uq_billing_invoices_sequence UNIQUE (financial_year, sequence_number),
    CONSTRAINT ck_billing_invoices_status
        CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'VOID', 'REFUNDED')),
    CONSTRAINT ck_billing_invoices_amounts CHECK (
        subtotal_paise >= 0 AND discount_paise >= 0
        AND cgst_paise >= 0 AND sgst_paise >= 0 AND igst_paise >= 0
        AND total_paise = subtotal_paise - discount_paise + cgst_paise + sgst_paise + igst_paise),
    -- IGST and CGST/SGST are mutually exclusive under GST.
    CONSTRAINT ck_billing_invoices_tax_exclusive
        CHECK (NOT (igst_paise > 0 AND (cgst_paise > 0 OR sgst_paise > 0))),
    CONSTRAINT fk_billing_invoices_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_invoices_order
        FOREIGN KEY (order_id) REFERENCES billing_orders (id) ON DELETE SET NULL,
    CONSTRAINT fk_billing_invoices_subscription
        FOREIGN KEY (subscription_id) REFERENCES billing_subscriptions (id) ON DELETE SET NULL,
    CONSTRAINT fk_billing_invoices_pdf
        FOREIGN KEY (pdf_file_id) REFERENCES stored_files (id) ON DELETE SET NULL
);

CREATE INDEX ix_billing_invoices_org ON billing_invoices (organization_id, issue_date DESC, id DESC);
CREATE INDEX ix_billing_invoices_order ON billing_invoices (order_id);
CREATE INDEX ix_billing_invoices_subscription ON billing_invoices (subscription_id);
CREATE INDEX ix_billing_invoices_status ON billing_invoices (status, due_date)
    WHERE status = 'ISSUED';
CREATE INDEX ix_billing_invoices_pdf ON billing_invoices (pdf_file_id);


-- -----------------------------------------------------------------------------
-- billing_invoice_counters: gapless sequence per financial year.
--
-- A Postgres sequence would leave gaps on rollback, which GST audits query.
-- A single row per year taken with SELECT ... FOR UPDATE serialises numbering.
-- -----------------------------------------------------------------------------
CREATE TABLE billing_invoice_counters (
    financial_year  varchar(9) PRIMARY KEY,
    last_sequence   integer     NOT NULL DEFAULT 0,
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_invoice_counters_sequence CHECK (last_sequence >= 0)
);

COMMENT ON TABLE billing_invoice_counters IS
    'Gapless invoice numbering. Locked with SELECT ... FOR UPDATE; do not replace '
    'with a sequence, which would leave gaps on rollback.';


-- -----------------------------------------------------------------------------
-- billing_webhook_events: raw gateway callbacks, persisted before processing.
--
-- Webhooks are the source of truth for payment state, not the browser redirect.
-- Storing the payload first means a processing bug can be fixed and replayed,
-- and the unique event id makes redelivery idempotent.
-- -----------------------------------------------------------------------------
CREATE TABLE billing_webhook_events (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    provider            varchar(24) NOT NULL DEFAULT 'RAZORPAY',
    -- Razorpay's x-razorpay-event-id. Unique, so redelivery is a no-op.
    provider_event_id   varchar(120),
    event_type          varchar(80) NOT NULL,
    payload             jsonb       NOT NULL,
    signature           varchar(255),
    signature_verified  boolean     NOT NULL DEFAULT false,
    -- PENDING | PROCESSED | FAILED | IGNORED
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts            integer     NOT NULL DEFAULT 0,
    last_error          varchar(2000),
    -- Resolved during processing; null when the event names an unknown order.
    organization_id     uuid,
    order_id            uuid,
    received_at         timestamptz NOT NULL DEFAULT now(),
    processed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_webhook_events_status
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'IGNORED')),
    CONSTRAINT ck_webhook_events_attempts CHECK (attempts >= 0),
    CONSTRAINT fk_webhook_events_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL,
    CONSTRAINT fk_webhook_events_order
        FOREIGN KEY (order_id) REFERENCES billing_orders (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_webhook_events_provider_event
    ON billing_webhook_events (provider, provider_event_id)
    WHERE provider_event_id IS NOT NULL;
CREATE INDEX ix_webhook_events_pending ON billing_webhook_events (received_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_webhook_events_org  ON billing_webhook_events (organization_id, received_at DESC);
CREATE INDEX ix_webhook_events_type ON billing_webhook_events (event_type, received_at DESC);
CREATE INDEX ix_webhook_events_order ON billing_webhook_events (order_id);


-- -----------------------------------------------------------------------------
-- Seed the public price list. Amounts in paise, tax added at invoice time.
-- -----------------------------------------------------------------------------
INSERT INTO billing_plans
    (plan_key, name, description, interval_type, amount_paise, per_seat_paise,
     included_seats, max_seats, trial_days, entitlements, is_public, rank)
VALUES
('starter-monthly', 'Starter',
 'For small teams getting their first shared inbox in place.',
 'MONTHLY', 0, 0, 3, 3, 0,
 '{"mailboxes":1,"mailDomains":1,"customRoles":false,"apiAccess":false,'
 '"auditRetentionDays":7,"slaPolicies":false,"support":"community"}'::jsonb,
 true, 10),

('growth-monthly', 'Growth',
 'Shared inboxes, routing, and SLAs for a growing support team.',
 'MONTHLY', 249900, 29900, 10, 50, 14,
 '{"mailboxes":5,"mailDomains":2,"customRoles":true,"apiAccess":true,'
 '"auditRetentionDays":90,"slaPolicies":true,"support":"email"}'::jsonb,
 true, 20),

('growth-annual', 'Growth (annual)',
 'Growth billed yearly, two months free.',
 'ANNUAL', 2499000, 299000, 10, 50, 14,
 '{"mailboxes":5,"mailDomains":2,"customRoles":true,"apiAccess":true,'
 '"auditRetentionDays":90,"slaPolicies":true,"support":"email"}'::jsonb,
 true, 21),

('business-monthly', 'Business',
 'Self-hosted mail, unlimited inboxes, and priority support.',
 'MONTHLY', 799900, 24900, 25, 500, 14,
 '{"mailboxes":25,"mailDomains":10,"customRoles":true,"apiAccess":true,'
 '"auditRetentionDays":365,"slaPolicies":true,"selfHostedMail":true,'
 '"support":"priority"}'::jsonb,
 true, 30),

('business-annual', 'Business (annual)',
 'Business billed yearly, two months free.',
 'ANNUAL', 7999000, 249000, 25, 500, 14,
 '{"mailboxes":25,"mailDomains":10,"customRoles":true,"apiAccess":true,'
 '"auditRetentionDays":365,"slaPolicies":true,"selfHostedMail":true,'
 '"support":"priority"}'::jsonb,
 true, 31),

('enterprise-custom', 'Enterprise',
 'Unlimited scale, SSO enforcement, dedicated infrastructure, and an SLA-backed '
 'contract. Priced per engagement.',
 'CUSTOM', 0, 0, 1000, NULL, 30,
 '{"mailboxes":-1,"mailDomains":-1,"customRoles":true,"apiAccess":true,'
 '"auditRetentionDays":2555,"slaPolicies":true,"selfHostedMail":true,'
 '"ssoEnforcement":true,"dedicatedInfra":true,"support":"dedicated"}'::jsonb,
 true, 40)
ON CONFLICT (plan_key) DO NOTHING;

COMMENT ON COLUMN billing_plans.entitlements IS
    'Quota and capability map. -1 means unlimited. Read by EntitlementService '
    'before any action that consumes a quota.';
