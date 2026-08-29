-- What kind of Prabhix staff someone is, rather than only whether they are.
--
-- Until now platform access was one boolean, users.platform_admin. That was defensible while there was
-- one person; it stops being defensible the moment a support hire needs to look at a ticket, because
-- the same flag also grants break-glass token revocation and every tenant's data.
--
-- A separate table rather than columns on users: the grant is an event with an actor and a time, and
-- "who gave this person the ability to revoke anyone's session, and when" is a question that will be
-- asked. A boolean column cannot answer it.
--
-- users.platform_admin stays as the coarse "is staff at all" gate, since twenty call sites read it and
-- the security config keys off it. It is now derived: PlatformStaffService is the only writer, and it
-- sets the flag when the first role is granted and clears it when the last is revoked. One writer, so
-- the two cannot drift.

CREATE TABLE platform_staff_roles
(
    id          uuid PRIMARY KEY,
    version     bigint      NOT NULL DEFAULT 0,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- SUPPORT   read a tenant's data to answer a ticket; no writes, no billing, no revocation
    -- BILLING   subscriptions, invoices and plan changes; no tenant content
    -- OPERATOR  platform health, queues, retries and replays; no tenant content
    -- SECURITY  break-glass token revocation and the audit trail
    -- OWNER     everything, including granting these roles
    role        varchar(24) NOT NULL,

    granted_at  timestamptz NOT NULL DEFAULT now(),
    -- Nullable because the backfill below has no actor: these grants predate the table.
    granted_by  uuid REFERENCES users (id) ON DELETE SET NULL,
    revoked_at  timestamptz,
    revoked_by  uuid REFERENCES users (id) ON DELETE SET NULL,
    -- Why, for the audit trail. A revocation without a reason is the one you cannot explain later.
    note        text,

    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid
);

-- One live grant per person per role. Without this, granting twice then revoking once leaves the
-- person still holding it, which is the worst possible outcome for a revocation path.
CREATE UNIQUE INDEX uq_platform_staff_roles_live
    ON platform_staff_roles (user_id, role) WHERE revoked_at IS NULL;

CREATE INDEX ix_platform_staff_roles_user
    ON platform_staff_roles (user_id) WHERE revoked_at IS NULL;

-- Existing platform admins become OWNER, which is what the boolean actually meant. Deliberately not
-- split into narrower roles by guesswork: whoever holds the flag today has everything, and silently
-- reducing their access during a migration would break something at an unpredictable moment. Narrowing
-- is a decision to take deliberately, from the console, once the roles exist.
INSERT INTO platform_staff_roles (id, user_id, role, granted_at, note)
SELECT gen_random_uuid(), id, 'OWNER', now(),
       'Backfilled from users.platform_admin when platform staff roles were introduced'
FROM users
WHERE platform_admin AND deleted_at IS NULL;
