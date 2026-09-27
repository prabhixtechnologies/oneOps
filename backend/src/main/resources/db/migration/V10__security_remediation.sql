-- Security remediation: idempotent discount redemption and invite-user reconciliation support.

CREATE UNIQUE INDEX IF NOT EXISTS ux_commerce_discount_redemptions_order
    ON commerce_discount_redemptions (order_id)
    WHERE order_id IS NOT NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reconciled_from_user_id uuid;

COMMENT ON COLUMN users.reconciled_from_user_id IS
    'When duplicate invite-era rows are merged, the surviving user id this row was folded into.';

INSERT INTO permissions (code, category, description, assignable, created_at)
VALUES ('DEVICE_PUSH_REGISTER', 'PLATFORM', 'Register mobile push notification devices', true, now())
ON CONFLICT (code) DO NOTHING;
