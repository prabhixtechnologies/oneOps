-- Supports bounded dunning sweeps ordered by grace-period expiry.
CREATE INDEX ix_subscriptions_past_due_grace
    ON billing_subscriptions (grace_period_ends_at ASC NULLS LAST, id)
    WHERE status = 'PAST_DUE';
