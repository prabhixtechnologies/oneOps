-- Dunning retry schedule and saved payment token for off-session renewal charges.
ALTER TABLE billing_subscriptions
    ADD COLUMN IF NOT EXISTS next_dunning_retry_at timestamptz,
    ADD COLUMN IF NOT EXISTS razorpay_token_id varchar(80);

CREATE INDEX IF NOT EXISTS ix_subscriptions_dunning_retry
    ON billing_subscriptions (next_dunning_retry_at)
    WHERE status = 'PAST_DUE' AND failed_payment_count < 3;

COMMENT ON COLUMN billing_subscriptions.next_dunning_retry_at IS
    'Earliest time the dunning job may attempt another off-session charge. '
    'Survives restarts so retries are not duplicated or lost.';
COMMENT ON COLUMN billing_subscriptions.razorpay_token_id IS
    'Token from the last successful checkout; used for renewal and dunning retries.';
