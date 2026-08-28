-- =============================================================================
-- V1  Core foundation: extensions, organizations, users, authentication
-- =============================================================================
-- Conventions used by every migration in this project:
--   * Primary keys are uuid, generated server-side with gen_random_uuid().
--   * Every table carries created_at / updated_at; tenant tables carry organization_id.
--   * Every foreign key gets an explicit index. Postgres indexes the referenced side
--     automatically but not the referencing side, and the referencing side is what
--     joins and cascade checks scan.
--   * "version" backs JPA optimistic locking.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid(), digest()
CREATE EXTENSION IF NOT EXISTS citext;     -- case-insensitive email/domain identity
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- fuzzy search on names and subjects
CREATE EXTENSION IF NOT EXISTS unaccent;   -- fold diacritics for search


-- -----------------------------------------------------------------------------
-- organizations: the tenant boundary. Every tenant-scoped row points here.
-- -----------------------------------------------------------------------------
CREATE TABLE organizations (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    name                varchar(200) NOT NULL,
    slug                varchar(80) NOT NULL,
    legal_name          varchar(250),
    -- India-specific tax identifiers, needed on every GST invoice we raise.
    gstin               varchar(15),
    pan                 varchar(10),
    billing_email       citext,
    billing_address     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    phone               varchar(32),
    website             varchar(255),
    logo_url            varchar(500),
    timezone            varchar(64) NOT NULL DEFAULT 'Asia/Kolkata',
    locale              varchar(16) NOT NULL DEFAULT 'en-IN',
    currency            varchar(3)  NOT NULL DEFAULT 'INR',
    -- ACTIVE | TRIAL | SUSPENDED | CANCELLED | DELETED
    status              varchar(24) NOT NULL DEFAULT 'TRIAL',
    trial_ends_at       timestamptz,
    -- Denormalised counter maintained on membership change. A COUNT(*) over
    -- 100k members on every page render is not affordable.
    member_count        integer     NOT NULL DEFAULT 0,
    seat_limit          integer     NOT NULL DEFAULT 5,
    settings            jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted_at          timestamptz,

    CONSTRAINT uq_organizations_slug UNIQUE (slug),
    CONSTRAINT ck_organizations_status
        CHECK (status IN ('ACTIVE', 'TRIAL', 'SUSPENDED', 'CANCELLED', 'DELETED')),
    CONSTRAINT ck_organizations_slug_format CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,79}$'),
    CONSTRAINT ck_organizations_seat_limit CHECK (seat_limit > 0),
    CONSTRAINT ck_organizations_member_count CHECK (member_count >= 0)
);

CREATE INDEX ix_organizations_status  ON organizations (status) WHERE deleted_at IS NULL;
CREATE INDEX ix_organizations_name_trgm ON organizations USING gin (name gin_trgm_ops);

COMMENT ON TABLE organizations IS
    'Tenant boundary. Rows in tenant-scoped tables are filtered by organization_id.';
COMMENT ON COLUMN organizations.member_count IS
    'Denormalised; maintained by OrganizationMemberService on join/leave.';


-- -----------------------------------------------------------------------------
-- users: a global identity. One user may belong to many organizations, so this
-- table is deliberately NOT tenant-scoped.
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    email                   citext      NOT NULL,
    email_verified_at       timestamptz,
    phone                   varchar(32),
    phone_verified_at       timestamptz,
    -- Null for accounts that only ever use magic link, OTP, or SSO.
    password_hash           varchar(120),
    password_changed_at     timestamptz,
    full_name               varchar(160) NOT NULL,
    display_name            varchar(80),
    avatar_url              varchar(500),
    job_title               varchar(120),
    timezone                varchar(64) NOT NULL DEFAULT 'Asia/Kolkata',
    locale                  varchar(16) NOT NULL DEFAULT 'en-IN',
    -- ACTIVE | INVITED | DISABLED | LOCKED
    status                  varchar(24) NOT NULL DEFAULT 'ACTIVE',
    -- Prabhix staff flag. Grants PLATFORM_ADMIN, never granted to a customer role.
    platform_admin          boolean     NOT NULL DEFAULT false,
    failed_login_attempts   integer     NOT NULL DEFAULT 0,
    locked_until            timestamptz,
    last_login_at           timestamptz,
    last_active_at          timestamptz,
    -- Last organization the user worked in, so the console can restore context.
    default_organization_id uuid,
    notification_prefs      jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED', 'LOCKED')),
    CONSTRAINT ck_users_email_shape CHECK (position('@' IN email) > 1),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_login_attempts >= 0),
    CONSTRAINT fk_users_default_org
        FOREIGN KEY (default_organization_id) REFERENCES organizations (id) ON DELETE SET NULL
);

CREATE INDEX ix_users_default_org    ON users (default_organization_id);
CREATE INDEX ix_users_status         ON users (status) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_phone          ON users (phone) WHERE phone IS NOT NULL;
CREATE INDEX ix_users_name_trgm      ON users USING gin (full_name gin_trgm_ops);
CREATE INDEX ix_users_platform_admin ON users (platform_admin) WHERE platform_admin = true;

COMMENT ON COLUMN users.password_hash IS
    'BCrypt. Null is legitimate: passwordless-only accounts never set one.';


-- -----------------------------------------------------------------------------
-- auth_identities: external SSO subjects linked to a user.
-- -----------------------------------------------------------------------------
CREATE TABLE auth_identities (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    user_id         uuid        NOT NULL,
    -- GOOGLE | MICROSOFT | GITHUB | SAML
    provider        varchar(32) NOT NULL,
    -- The provider's immutable subject id, never the email: emails get reassigned.
    provider_subject varchar(255) NOT NULL,
    provider_email  citext,
    raw_profile     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    linked_at       timestamptz NOT NULL DEFAULT now(),
    last_login_at   timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_auth_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT ck_auth_identities_provider
        CHECK (provider IN ('GOOGLE', 'MICROSOFT', 'GITHUB', 'SAML')),
    CONSTRAINT fk_auth_identities_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_auth_identities_user ON auth_identities (user_id);


-- -----------------------------------------------------------------------------
-- device_sessions: one row per signed-in device, and the anchor for refresh
-- tokens so "sign out this device" is a single update.
-- -----------------------------------------------------------------------------
CREATE TABLE device_sessions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    user_id             uuid        NOT NULL,
    -- Client-generated stable id, so re-signing in on the same device reuses the row.
    device_id           varchar(128),
    device_name         varchar(160),
    -- WEB | IOS | ANDROID | API
    device_type         varchar(24) NOT NULL DEFAULT 'WEB',
    user_agent          varchar(500),
    ip_address          varchar(45),
    last_seen_at        timestamptz NOT NULL DEFAULT now(),
    revoked_at          timestamptz,
    revoked_reason      varchar(64),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_device_sessions_type
        CHECK (device_type IN ('WEB', 'IOS', 'ANDROID', 'API')),
    CONSTRAINT fk_device_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_device_sessions_user   ON device_sessions (user_id, last_seen_at DESC);
CREATE INDEX ix_device_sessions_active ON device_sessions (user_id) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX uq_device_sessions_user_device
    ON device_sessions (user_id, device_id) WHERE device_id IS NOT NULL AND revoked_at IS NULL;


-- -----------------------------------------------------------------------------
-- refresh_tokens: opaque, single-use, rotated on every refresh.
--
-- Only a SHA-256 hash is stored. A database leak then yields nothing usable,
-- which is the same reasoning as never storing a raw password.
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    user_id         uuid        NOT NULL,
    session_id      uuid        NOT NULL,
    token_hash      varchar(64) NOT NULL,
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz,
    revoked_at      timestamptz,
    -- Set when this token replaced an earlier one, giving a reuse-detection chain.
    replaced_by     uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_session
        FOREIGN KEY (session_id) REFERENCES device_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id) ON DELETE SET NULL
);

CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);
CREATE INDEX ix_refresh_tokens_expiry  ON refresh_tokens (expires_at)
    WHERE revoked_at IS NULL AND used_at IS NULL;
CREATE INDEX ix_refresh_tokens_replaced_by ON refresh_tokens (replaced_by);

COMMENT ON COLUMN refresh_tokens.replaced_by IS
    'Rotation chain. A refresh presented against an already-used token means theft; '
    'AuthService revokes the whole chain when it sees that.';


-- -----------------------------------------------------------------------------
-- auth_challenges: short-lived one-time secrets for passwordless flows.
-- Covers magic links, email/SMS OTP, password reset, and email verification.
-- -----------------------------------------------------------------------------
CREATE TABLE auth_challenges (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    -- MAGIC_LINK | EMAIL_OTP | SMS_OTP | WHATSAPP_OTP | PASSWORD_RESET | EMAIL_VERIFY
    purpose         varchar(32) NOT NULL,
    -- Nullable: a magic link to an address with no account yet still needs a row.
    user_id         uuid,
    -- Email or phone the challenge was sent to.
    destination     citext      NOT NULL,
    secret_hash     varchar(64) NOT NULL,
    expires_at      timestamptz NOT NULL,
    consumed_at     timestamptz,
    attempts        integer     NOT NULL DEFAULT 0,
    max_attempts    integer     NOT NULL DEFAULT 5,
    ip_address      varchar(45),
    -- Carries the post-login redirect and invite token through the round trip.
    metadata        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT ck_auth_challenges_purpose CHECK (purpose IN
        ('MAGIC_LINK', 'EMAIL_OTP', 'SMS_OTP', 'WHATSAPP_OTP', 'PASSWORD_RESET', 'EMAIL_VERIFY')),
    CONSTRAINT ck_auth_challenges_attempts CHECK (attempts >= 0 AND max_attempts > 0),
    CONSTRAINT fk_auth_challenges_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_auth_challenges_lookup ON auth_challenges (secret_hash)
    WHERE consumed_at IS NULL;
CREATE INDEX ix_auth_challenges_dest   ON auth_challenges (destination, purpose, created_at DESC);
CREATE INDEX ix_auth_challenges_user   ON auth_challenges (user_id);
CREATE INDEX ix_auth_challenges_expiry ON auth_challenges (expires_at) WHERE consumed_at IS NULL;
