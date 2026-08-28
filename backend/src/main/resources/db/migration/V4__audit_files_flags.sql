-- =============================================================================
-- V4  Audit log, stored files, feature flags, in-app notifications
-- =============================================================================

-- -----------------------------------------------------------------------------
-- audit_logs: append-only. Never updated, never deleted by application code.
--
-- Range-partitioned by month because this is the fastest-growing table in the
-- system and retention is enforced by detaching old partitions, which is
-- instant, rather than by DELETE, which would bloat and vacuum for hours.
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              uuid        NOT NULL DEFAULT gen_random_uuid(),
    -- Null for platform-level actions that sit outside any tenant.
    organization_id uuid,
    actor_user_id   uuid,
    actor_email     citext,
    -- USER | API_KEY | SYSTEM | PLATFORM_ADMIN
    actor_type      varchar(24) NOT NULL DEFAULT 'USER',
    -- Dotted verb: mail.thread.assigned, org.member.removed, billing.plan.changed
    action          varchar(80) NOT NULL,
    -- Target of the action, e.g. ('mail_thread', <uuid>)
    resource_type   varchar(64),
    resource_id     uuid,
    resource_label  varchar(255),
    -- Field-level before/after for update actions.
    changes         jsonb,
    metadata        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    ip_address      varchar(45),
    user_agent      varchar(500),
    -- SUCCESS | FAILURE
    outcome         varchar(16) NOT NULL DEFAULT 'SUCCESS',
    created_at      timestamptz NOT NULL DEFAULT now(),

    -- The partition key must be part of every unique constraint, so the primary
    -- key is composite rather than id alone.
    CONSTRAINT pk_audit_logs PRIMARY KEY (id, created_at),
    CONSTRAINT ck_audit_logs_actor_type
        CHECK (actor_type IN ('USER', 'API_KEY', 'SYSTEM', 'PLATFORM_ADMIN')),
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
) PARTITION BY RANGE (created_at);

-- Indexes declared on the parent propagate to every partition, existing and future.
CREATE INDEX ix_audit_logs_org_listing
    ON audit_logs (organization_id, created_at DESC, id DESC);
CREATE INDEX ix_audit_logs_actor    ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_logs_action   ON audit_logs (organization_id, action, created_at DESC);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id, created_at DESC);

-- Bootstrap partitions. AuditPartitionMaintenanceJob creates each following
-- month ahead of time; a DEFAULT partition means an insert never fails even if
-- that job has not run.
CREATE TABLE audit_logs_2026_08 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE audit_logs_2026_09 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_logs_2026_10 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

COMMENT ON TABLE audit_logs IS
    'Append-only. Partitioned monthly; retention is enforced by detaching partitions.';


-- -----------------------------------------------------------------------------
-- stored_files: metadata for anything living in object storage. Bytes are never
-- in Postgres, only the pointer.
-- -----------------------------------------------------------------------------
CREATE TABLE stored_files (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    -- Key within the bucket. Unique so an upload cannot silently overwrite.
    storage_key         varchar(500) NOT NULL,
    bucket              varchar(120) NOT NULL,
    original_filename   varchar(255) NOT NULL,
    content_type        varchar(160) NOT NULL,
    size_bytes          bigint      NOT NULL,
    -- SHA-256 of the content, used to deduplicate repeated uploads.
    checksum_sha256     varchar(64),
    -- MAIL_ATTACHMENT | MAIL_RAW_MIME | AVATAR | LOGO | EXPORT | IMPORT | INVOICE
    purpose             varchar(32) NOT NULL,
    uploaded_by         uuid,
    -- PENDING | CLEAN | INFECTED | SKIPPED
    scan_status         varchar(16) NOT NULL DEFAULT 'PENDING',
    scanned_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted_at          timestamptz,

    CONSTRAINT uq_stored_files_key UNIQUE (bucket, storage_key),
    CONSTRAINT ck_stored_files_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_stored_files_purpose CHECK (purpose IN
        ('MAIL_ATTACHMENT', 'MAIL_RAW_MIME', 'AVATAR', 'LOGO', 'EXPORT', 'IMPORT', 'INVOICE')),
    CONSTRAINT ck_stored_files_scan
        CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'SKIPPED')),
    CONSTRAINT fk_stored_files_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_stored_files_uploader
        FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_stored_files_org      ON stored_files (organization_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_stored_files_uploader ON stored_files (uploaded_by);
CREATE INDEX ix_stored_files_checksum ON stored_files (organization_id, checksum_sha256)
    WHERE checksum_sha256 IS NOT NULL;


-- -----------------------------------------------------------------------------
-- feature_flags: platform default plus per-organization override.
-- -----------------------------------------------------------------------------
CREATE TABLE feature_flags (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    flag_key        varchar(80) NOT NULL,
    description     varchar(255),
    -- Value used when no organization override exists.
    default_enabled boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_feature_flags_key UNIQUE (flag_key)
);

CREATE TABLE feature_flag_overrides (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    flag_key        varchar(80) NOT NULL,
    enabled         boolean     NOT NULL,
    reason          varchar(255),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_flag_overrides UNIQUE (organization_id, flag_key),
    CONSTRAINT fk_flag_overrides_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_flag_overrides_flag
        FOREIGN KEY (flag_key) REFERENCES feature_flags (flag_key) ON DELETE CASCADE
);

CREATE INDEX ix_flag_overrides_org  ON feature_flag_overrides (organization_id);
CREATE INDEX ix_flag_overrides_flag ON feature_flag_overrides (flag_key);

INSERT INTO feature_flags (flag_key, description, default_enabled) VALUES
    ('mail.self_hosted_transport', 'Route outbound mail through our own Postfix rather than a relay', false),
    ('mail.open_tracking',         'Allow open and click tracking on non-auth templates', false),
    ('mail.ai_reply_suggestions',  'Suggest replies from thread history', false),
    ('billing.annual_plans',       'Offer annual billing with a discount', true),
    ('org.sso_enforcement',        'Allow an organization to require SSO for all members', false)
ON CONFLICT (flag_key) DO NOTHING;


-- -----------------------------------------------------------------------------
-- notifications: in-app notification centre.
-- -----------------------------------------------------------------------------
CREATE TABLE notifications (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    -- MAIL_ASSIGNED | MAIL_MENTIONED | SLA_BREACH | INVITE_ACCEPTED |
    -- PAYMENT_FAILED | PAYMENT_SUCCEEDED | SYSTEM
    kind            varchar(40) NOT NULL,
    title           varchar(200) NOT NULL,
    body            varchar(1000),
    -- Deep link into the console.
    link_url        varchar(500),
    resource_type   varchar(64),
    resource_id     uuid,
    read_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT fk_notifications_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_notifications_user
    ON notifications (user_id, created_at DESC, id DESC);
-- Serves the unread badge without touching read rows.
CREATE INDEX ix_notifications_unread
    ON notifications (user_id, organization_id) WHERE read_at IS NULL;
CREATE INDEX ix_notifications_org ON notifications (organization_id);
