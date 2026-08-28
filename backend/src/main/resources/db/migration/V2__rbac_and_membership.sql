-- =============================================================================
-- V2  RBAC: permissions, roles, memberships, teams, invitations, API keys
-- =============================================================================
-- Authorization model:
--   permissions          global vocabulary, mirrors the Permission enum
--   roles                bundles of permissions; system roles are shared across
--                        all tenants (organization_id IS NULL), custom roles are
--                        owned by one organization
--   memberships          user x organization x role
--   teams                grouping used for routing and reporting
-- =============================================================================


-- -----------------------------------------------------------------------------
-- permissions: the fixed vocabulary. Seeded from the Permission enum; a row is
-- only added by a migration, never at runtime.
-- -----------------------------------------------------------------------------
CREATE TABLE permissions (
    code            varchar(64) PRIMARY KEY,
    -- Grouping used to lay out the permission matrix in the console.
    category        varchar(32) NOT NULL,
    description     varchar(255) NOT NULL,
    -- False for PLATFORM_ADMIN, which no customer role may ever contain.
    assignable      boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_permissions_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE INDEX ix_permissions_category ON permissions (category) WHERE assignable = true;


-- -----------------------------------------------------------------------------
-- roles: system roles are global rows shared by every tenant, so adding a
-- permission to "Agent" takes one update instead of one per organization.
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    -- NULL marks a global system role. Non-null scopes a custom role to one tenant.
    organization_id uuid,
    -- Stable machine key: OWNER, ADMIN, or a slug for custom roles.
    role_key        varchar(64) NOT NULL,
    name            varchar(80) NOT NULL,
    description     varchar(255),
    is_system       boolean     NOT NULL DEFAULT false,
    -- Display order in the console, lowest first.
    rank            integer     NOT NULL DEFAULT 100,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT ck_roles_system_scope
        CHECK ((is_system AND organization_id IS NULL) OR (NOT is_system AND organization_id IS NOT NULL)),
    CONSTRAINT fk_roles_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

-- Two partial indexes rather than one composite: NULLs are distinct in a plain
-- UNIQUE constraint, which would let duplicate system keys slip through.
CREATE UNIQUE INDEX uq_roles_system_key ON roles (role_key) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uq_roles_custom_key ON roles (organization_id, role_key)
    WHERE organization_id IS NOT NULL;
CREATE INDEX ix_roles_organization ON roles (organization_id);


CREATE TABLE role_permissions (
    role_id         uuid        NOT NULL,
    permission_code varchar(64) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_code) REFERENCES permissions (code) ON DELETE CASCADE
);

CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_code);


-- -----------------------------------------------------------------------------
-- organization_memberships: the join that makes a user part of a tenant.
--
-- This is the hottest table in the system at 100k members, so it carries a
-- denormalised copy of the display name to render a member list or an assignee
-- picker without joining users.
-- -----------------------------------------------------------------------------
CREATE TABLE organization_memberships (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    user_id             uuid        NOT NULL,
    role_id             uuid        NOT NULL,
    -- ACTIVE | PENDING | SUSPENDED | LEFT
    status              varchar(24) NOT NULL DEFAULT 'ACTIVE',
    -- Denormalised from users for list rendering and search.
    display_name        varchar(160) NOT NULL,
    email               citext      NOT NULL,
    employee_id         varchar(64),
    department          varchar(120),
    joined_at           timestamptz NOT NULL DEFAULT now(),
    invited_by          uuid,
    last_active_at      timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_memberships_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_memberships_status
        CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED', 'LEFT')),
    CONSTRAINT fk_memberships_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_invited_by
        FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL
);

-- Covering index for the default member list: filter by org + status, sort by
-- the keyset key. Postgres can serve the page straight from this index.
CREATE INDEX ix_memberships_org_listing
    ON organization_memberships (organization_id, status, created_at DESC, id DESC);
CREATE INDEX ix_memberships_user        ON organization_memberships (user_id);
CREATE INDEX ix_memberships_role        ON organization_memberships (role_id);
CREATE INDEX ix_memberships_invited_by  ON organization_memberships (invited_by);
CREATE INDEX ix_memberships_name_trgm
    ON organization_memberships USING gin (display_name gin_trgm_ops);
CREATE INDEX ix_memberships_department
    ON organization_memberships (organization_id, department)
    WHERE department IS NOT NULL;

COMMENT ON COLUMN organization_memberships.display_name IS
    'Denormalised from users.full_name. Kept in step by OrganizationMemberService '
    'when a user renames themselves.';


-- -----------------------------------------------------------------------------
-- teams
-- -----------------------------------------------------------------------------
CREATE TABLE teams (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    slug            varchar(80) NOT NULL,
    name            varchar(120) NOT NULL,
    description     varchar(500),
    -- Optional lead, shown in the console and used as escalation target.
    lead_user_id    uuid,
    member_count    integer     NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_teams_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_teams_member_count CHECK (member_count >= 0),
    CONSTRAINT fk_teams_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_teams_lead
        FOREIGN KEY (lead_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_teams_organization ON teams (organization_id);
CREATE INDEX ix_teams_lead         ON teams (lead_user_id);


CREATE TABLE team_members (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    team_id         uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    -- MEMBER | LEAD
    team_role       varchar(24) NOT NULL DEFAULT 'MEMBER',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_team_members_team_user UNIQUE (team_id, user_id),
    CONSTRAINT ck_team_members_role CHECK (team_role IN ('MEMBER', 'LEAD')),
    CONSTRAINT fk_team_members_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_team
        FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_team_members_team ON team_members (team_id);
CREATE INDEX ix_team_members_user ON team_members (user_id);
CREATE INDEX ix_team_members_org  ON team_members (organization_id);


-- -----------------------------------------------------------------------------
-- invitations: pending membership offers. Only the token hash is stored.
-- -----------------------------------------------------------------------------
CREATE TABLE invitations (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    email           citext      NOT NULL,
    role_id         uuid        NOT NULL,
    team_id         uuid,
    token_hash      varchar(64) NOT NULL,
    -- PENDING | ACCEPTED | REVOKED | EXPIRED
    status          varchar(24) NOT NULL DEFAULT 'PENDING',
    message         varchar(1000),
    expires_at      timestamptz NOT NULL,
    accepted_at     timestamptz,
    accepted_by     uuid,
    revoked_at      timestamptz,
    invited_by      uuid        NOT NULL,
    reminder_count  integer     NOT NULL DEFAULT 0,
    last_sent_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_invitations_token UNIQUE (token_hash),
    CONSTRAINT ck_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT fk_invitations_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invitations_team
        FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE SET NULL,
    CONSTRAINT fk_invitations_invited_by
        FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invitations_accepted_by
        FOREIGN KEY (accepted_by) REFERENCES users (id) ON DELETE SET NULL
);

-- One live invite per address per organization; re-inviting reuses the row.
CREATE UNIQUE INDEX uq_invitations_pending_email
    ON invitations (organization_id, email) WHERE status = 'PENDING';
CREATE INDEX ix_invitations_org        ON invitations (organization_id, status, created_at DESC);
CREATE INDEX ix_invitations_email      ON invitations (email) WHERE status = 'PENDING';
CREATE INDEX ix_invitations_role       ON invitations (role_id);
CREATE INDEX ix_invitations_team       ON invitations (team_id);
CREATE INDEX ix_invitations_invited_by ON invitations (invited_by);
CREATE INDEX ix_invitations_accepted_by ON invitations (accepted_by);


-- -----------------------------------------------------------------------------
-- api_keys: machine access. Scoped to an organization and a permission subset.
-- -----------------------------------------------------------------------------
CREATE TABLE api_keys (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    name            varchar(120) NOT NULL,
    -- First 8 chars of the key, shown in the UI so a key can be identified
    -- without ever storing or displaying the secret again.
    key_prefix      varchar(16) NOT NULL,
    key_hash        varchar(64) NOT NULL,
    -- Permission codes granted, always a subset of the creator's own.
    scopes          jsonb       NOT NULL DEFAULT '[]'::jsonb,
    created_by_user uuid        NOT NULL,
    last_used_at    timestamptz,
    expires_at      timestamptz,
    revoked_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_api_keys_hash UNIQUE (key_hash),
    CONSTRAINT fk_api_keys_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_api_keys_creator
        FOREIGN KEY (created_by_user) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_api_keys_org     ON api_keys (organization_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_api_keys_creator ON api_keys (created_by_user);
