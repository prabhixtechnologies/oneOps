-- =============================================================================
-- V41  Push notification device tokens and transactional outbox
-- =============================================================================

CREATE TABLE push_tokens (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    user_id                 uuid        NOT NULL,
    -- FCM | APNS
    platform                varchar(8)  NOT NULL,
    token                   varchar(512) NOT NULL,
    device_id               varchar(120) NOT NULL,
    device_name             varchar(160),
    app_version             varchar(32),
    last_seen_at            timestamptz NOT NULL DEFAULT now(),
    enabled                 boolean     NOT NULL DEFAULT true,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_push_tokens_token UNIQUE (token),
    CONSTRAINT ck_push_tokens_platform CHECK (platform IN ('FCM', 'APNS')),
    CONSTRAINT fk_push_tokens_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_push_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_push_tokens_user_org
    ON push_tokens (organization_id, user_id, last_seen_at DESC)
    WHERE deleted_at IS NULL AND enabled = true;

CREATE INDEX ix_push_tokens_device
    ON push_tokens (organization_id, user_id, device_id)
    WHERE deleted_at IS NULL;


CREATE TABLE push_outbox (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    user_id                 uuid        NOT NULL,
    push_token_id           uuid        NOT NULL,
    notification_type       varchar(64) NOT NULL,
    payload                 jsonb       NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key              varchar(200),
    -- PENDING | CLAIMED | SENDING | SENT | FAILED | DEAD
    status                  varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts                integer     NOT NULL DEFAULT 0,
    max_attempts            integer     NOT NULL DEFAULT 6,
    scheduled_at            timestamptz NOT NULL DEFAULT now(),
    next_attempt_at         timestamptz,
    claimed_at              timestamptz,
    claimed_by              varchar(80),
    sent_at                 timestamptz,
    last_error              varchar(2000),
    provider_used           varchar(32),
    provider_message_id     varchar(255),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT ck_push_outbox_status CHECK (status IN
        ('PENDING', 'CLAIMED', 'SENDING', 'SENT', 'FAILED', 'DEAD')),
    CONSTRAINT ck_push_outbox_attempts CHECK (attempts >= 0 AND max_attempts > 0),
    CONSTRAINT ck_push_outbox_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT fk_push_outbox_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_push_outbox_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_push_outbox_token
        FOREIGN KEY (push_token_id) REFERENCES push_tokens (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_push_outbox_dedupe
    ON push_outbox (dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE INDEX ix_push_outbox_pending
    ON push_outbox (status, scheduled_at, id)
    WHERE status IN ('PENDING', 'FAILED');
