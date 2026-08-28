-- Pending plan/seat downgrades scheduled for period end; invoice idempotency per order.
ALTER TABLE billing_subscriptions
    ADD COLUMN pending_plan_id   uuid,
    ADD COLUMN pending_seats     integer,
    ADD COLUMN pending_change_at timestamptz,
    ADD CONSTRAINT fk_subscriptions_pending_plan
        FOREIGN KEY (pending_plan_id) REFERENCES billing_plans (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_subscriptions_pending_seats
        CHECK (pending_seats IS NULL OR pending_seats > 0);

CREATE UNIQUE INDEX uq_billing_invoices_order
    ON billing_invoices (order_id)
    WHERE order_id IS NOT NULL;

CREATE INDEX ix_subscriptions_pending_change
    ON billing_subscriptions (pending_change_at)
    WHERE pending_change_at IS NOT NULL;
