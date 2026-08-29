-- Domains an organization has proved it controls.
--
-- Two things depend on this. Invitations can be restricted to addresses inside a verified domain, so a
-- tenant admin cannot mistakenly - or deliberately - invite an address they have no relationship with
-- into their organization. And auto-join lets somebody who signs up with a matching address land in
-- the right organization without an invitation at all, which is the difference between onboarding a
-- fifty-person company one email at a time and not.
--
-- Verification is mandatory before either applies. An unverified claim on a domain is worth nothing:
-- letting one org claim `gmail.com` and auto-join every Gmail user is the obvious abuse, and letting
-- it claim a competitor's domain to intercept their staff is the less obvious one.

CREATE TABLE organization_domains
(
    id                    uuid PRIMARY KEY,
    version               bigint      NOT NULL DEFAULT 0,
    organization_id       uuid        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,

    -- citext because DNS is case-insensitive and PRABHIX.COM is the same domain as prabhix.com. The
    -- application normalises on write as well, for the reason spelled out in Emails.java: the JDBC
    -- driver binds String as varchar, so citext does not fold case for a parameterised comparison.
    domain                citext      NOT NULL,

    -- The value that must appear in a TXT record at _prabhix-verify.<domain>. Random per row, so
    -- proving control of one domain tells an attacker nothing about the token for another.
    verification_token    varchar(64) NOT NULL,
    verified_at           timestamptz,
    last_checked_at       timestamptz,
    -- Why the last check failed, shown in the console so the admin can act on it rather than being
    -- told only that it did not work.
    last_check_error      text,

    -- Off by default, and separate from verification on purpose. Proving you own a domain is not the
    -- same decision as agreeing that anyone with an address there should get into your organization,
    -- and a contractor at a large company would be surprised by the second.
    auto_join_enabled     boolean     NOT NULL DEFAULT false,
    -- Which role an auto-joined member receives. Null means the organization's default.
    auto_join_role_id     uuid REFERENCES roles (id) ON DELETE SET NULL,

    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    deleted_at            timestamptz
);

-- One live claim per domain across the whole platform. Two organizations both verified for the same
-- domain would make auto-join ambiguous, and an invitation restriction meaningless - whichever row was
-- read first would decide. Partial, so a released domain can be claimed again.
CREATE UNIQUE INDEX uq_organization_domains_domain
    ON organization_domains (domain) WHERE deleted_at IS NULL;

CREATE INDEX ix_organization_domains_org
    ON organization_domains (organization_id) WHERE deleted_at IS NULL;

-- The lookup on the auto-join path, which runs on sign-up. Partial on both conditions so the index
-- only holds rows that could actually match.
CREATE INDEX ix_organization_domains_auto_join
    ON organization_domains (domain)
    WHERE deleted_at IS NULL AND verified_at IS NOT NULL AND auto_join_enabled;
