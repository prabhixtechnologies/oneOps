-- =============================================================================
-- V5  Mail core: domains, mailboxes, aliases, threads, messages, attachments
-- =============================================================================

-- -----------------------------------------------------------------------------
-- mail_domains: a domain the organization sends and receives mail for.
--
-- Self-hosting mail fails on deliverability, not software, so verification state
-- for each DNS record is first-class data rather than a boolean.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_domains (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    domain                  citext      NOT NULL,
    -- PENDING | VERIFYING | VERIFIED | FAILED | DISABLED
    status                  varchar(24) NOT NULL DEFAULT 'PENDING',
    -- SELF_HOSTED: our Postfix owns MX. EXTERNAL_IMAP: mail lives at Google or
    -- Zoho and we only pull from it. RELAY_ONLY: we send as this domain but do
    -- not receive.
    mode                    varchar(24) NOT NULL DEFAULT 'EXTERNAL_IMAP',
    -- Random token published as a TXT record to prove domain ownership.
    verification_token      varchar(80) NOT NULL,
    dkim_selector           varchar(63) NOT NULL DEFAULT 'pbx1',
    dkim_public_key         text,
    -- Encrypted at the application layer before it is written here.
    dkim_private_key_enc    text,
    mx_verified_at          timestamptz,
    spf_verified_at         timestamptz,
    dkim_verified_at        timestamptz,
    dmarc_verified_at       timestamptz,
    ownership_verified_at   timestamptz,
    last_checked_at         timestamptz,
    -- Per-record detail for the DNS setup screen: name, type, expected, observed.
    dns_report              jsonb       NOT NULL DEFAULT '{}'::jsonb,
    is_default              boolean     NOT NULL DEFAULT false,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    -- Global, not per-tenant: one domain can only be claimed once across the
    -- whole platform, otherwise two tenants could receive each other's mail.
    CONSTRAINT uq_mail_domains_domain UNIQUE (domain),
    CONSTRAINT ck_mail_domains_status
        CHECK (status IN ('PENDING', 'VERIFYING', 'VERIFIED', 'FAILED', 'DISABLED')),
    CONSTRAINT ck_mail_domains_mode
        CHECK (mode IN ('SELF_HOSTED', 'EXTERNAL_IMAP', 'RELAY_ONLY')),
    CONSTRAINT fk_mail_domains_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX ix_mail_domains_org ON mail_domains (organization_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_mail_domains_one_default
    ON mail_domains (organization_id) WHERE is_default = true AND deleted_at IS NULL;

COMMENT ON COLUMN mail_domains.dkim_private_key_enc IS
    'AES-GCM encrypted with the platform data key. Never returned by any API.';


-- -----------------------------------------------------------------------------
-- mail_mailboxes: a shared address such as support@ or sales@, with its own
-- members, routing, signature, and SLA.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_mailboxes (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    mail_domain_id          uuid,
    -- Local part plus domain, stored whole so lookups need no join.
    address                 citext      NOT NULL,
    name                    varchar(120) NOT NULL,
    description             varchar(500),
    -- SHARED: a team inbox. PERSONAL: one member's own address. SYSTEM: outbound
    -- only, e.g. no-reply@.
    kind                    varchar(16) NOT NULL DEFAULT 'SHARED',
    -- ACTIVE | PAUSED | ARCHIVED
    status                  varchar(16) NOT NULL DEFAULT 'ACTIVE',
    colour                  varchar(9),
    signature_html          text,
    -- Address replies come from when it should differ from `address`.
    reply_to                citext,
    -- IMAP connection for EXTERNAL_IMAP domains.
    imap_host               varchar(255),
    imap_port               integer,
    imap_username           varchar(255),
    imap_password_enc       text,
    imap_use_ssl            boolean     NOT NULL DEFAULT true,
    imap_folder             varchar(255) NOT NULL DEFAULT 'INBOX',
    -- IMAP UID cursor. UIDs are only meaningful within a UIDVALIDITY generation;
    -- if the server changes it, we must resynchronise rather than trust last_uid.
    imap_last_uid           bigint      NOT NULL DEFAULT 0,
    imap_uid_validity       bigint,
    imap_last_polled_at     timestamptz,
    imap_last_error         varchar(500),
    imap_consecutive_errors integer     NOT NULL DEFAULT 0,
    -- SMTP override, when this mailbox must send through a different provider.
    smtp_host               varchar(255),
    smtp_port               integer,
    smtp_username           varchar(255),
    smtp_password_enc       text,
    -- Auto-reply sent once per thread on first inbound message.
    auto_reply_enabled      boolean     NOT NULL DEFAULT false,
    auto_reply_subject      varchar(255),
    auto_reply_body_html    text,
    -- First-response and resolution targets, in business minutes.
    sla_first_response_mins integer,
    sla_resolution_mins     integer,
    -- Working hours per weekday plus holidays, used to convert business minutes
    -- into a wall-clock due time.
    business_hours          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    timezone                varchar(64) NOT NULL DEFAULT 'Asia/Kolkata',
    -- Counters kept current on thread transitions; recomputing them per request
    -- would mean scanning every thread in the mailbox.
    open_thread_count       integer     NOT NULL DEFAULT 0,
    unassigned_count        integer     NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_mail_mailboxes_address UNIQUE (address),
    CONSTRAINT ck_mail_mailboxes_kind CHECK (kind IN ('SHARED', 'PERSONAL', 'SYSTEM')),
    CONSTRAINT ck_mail_mailboxes_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT ck_mail_mailboxes_counts
        CHECK (open_thread_count >= 0 AND unassigned_count >= 0),
    CONSTRAINT ck_mail_mailboxes_imap_port
        CHECK (imap_port IS NULL OR (imap_port > 0 AND imap_port <= 65535)),
    CONSTRAINT fk_mail_mailboxes_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_mailboxes_domain
        FOREIGN KEY (mail_domain_id) REFERENCES mail_domains (id) ON DELETE SET NULL
);

CREATE INDEX ix_mail_mailboxes_org ON mail_mailboxes (organization_id, status)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_mail_mailboxes_domain ON mail_mailboxes (mail_domain_id);
-- Drives the IMAP poll scheduler: due mailboxes, oldest first.
CREATE INDEX ix_mail_mailboxes_imap_due ON mail_mailboxes (imap_last_polled_at)
    WHERE status = 'ACTIVE' AND imap_host IS NOT NULL AND deleted_at IS NULL;


-- -----------------------------------------------------------------------------
-- mail_aliases: extra addresses that deliver into a mailbox.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_aliases (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    mailbox_id      uuid        NOT NULL,
    address         citext      NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_mail_aliases_address UNIQUE (address),
    CONSTRAINT fk_mail_aliases_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_aliases_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE CASCADE
);

CREATE INDEX ix_mail_aliases_mailbox ON mail_aliases (mailbox_id);
CREATE INDEX ix_mail_aliases_org     ON mail_aliases (organization_id);


-- -----------------------------------------------------------------------------
-- mail_mailbox_members: who can see and work a shared inbox.
--
-- This is what stops a support agent reading hr@. A member of the organization
-- with MAIL_READ still sees nothing until they are a member of a mailbox,
-- unless they hold MAIL_READ_ALL.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_mailbox_members (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    mailbox_id      uuid        NOT NULL,
    -- Exactly one of user_id / team_id is set, so a mailbox can be granted to a
    -- whole team without enumerating its members.
    user_id         uuid,
    team_id         uuid,
    -- MEMBER | LEAD
    access_level    varchar(16) NOT NULL DEFAULT 'MEMBER',
    -- Include this mailbox in the member's notification stream.
    notify          boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT ck_mailbox_members_subject
        CHECK ((user_id IS NOT NULL AND team_id IS NULL)
            OR (user_id IS NULL AND team_id IS NOT NULL)),
    CONSTRAINT ck_mailbox_members_access CHECK (access_level IN ('MEMBER', 'LEAD')),
    CONSTRAINT fk_mailbox_members_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mailbox_members_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE CASCADE,
    CONSTRAINT fk_mailbox_members_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_mailbox_members_team
        FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_mailbox_members_user
    ON mail_mailbox_members (mailbox_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_mailbox_members_team
    ON mail_mailbox_members (mailbox_id, team_id) WHERE team_id IS NOT NULL;
CREATE INDEX ix_mailbox_members_user ON mail_mailbox_members (user_id);
CREATE INDEX ix_mailbox_members_team ON mail_mailbox_members (team_id);
CREATE INDEX ix_mailbox_members_org  ON mail_mailbox_members (organization_id);


-- -----------------------------------------------------------------------------
-- mail_threads: a conversation. The primary object agents work with.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_threads (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    mailbox_id              uuid        NOT NULL,
    -- Short human-quotable reference, also injected into outbound subjects so
    -- replies rejoin the thread when a remote mailer strips References.
    reference_key            varchar(24) NOT NULL,
    subject                 varchar(500) NOT NULL,
    -- Subject with Re:/Fwd: prefixes and whitespace removed, for the fallback
    -- threading heuristic.
    normalized_subject      varchar(500) NOT NULL,
    -- OPEN | PENDING_CUSTOMER | ON_HOLD | RESOLVED | CLOSED | SPAM | TRASH
    status                  varchar(24) NOT NULL DEFAULT 'OPEN',
    -- LOW | NORMAL | HIGH | URGENT
    priority                varchar(16) NOT NULL DEFAULT 'NORMAL',
    assignee_user_id        uuid,
    assignee_team_id        uuid,
    assigned_at            timestamptz,
    assigned_by            uuid,
    -- Denormalised participant summary so a thread list row needs no join.
    customer_email         citext,
    customer_name          varchar(200),
    participant_emails     jsonb       NOT NULL DEFAULT '[]'::jsonb,
    message_count          integer     NOT NULL DEFAULT 0,
    unread_count           integer     NOT NULL DEFAULT 0,
    has_attachments        boolean     NOT NULL DEFAULT false,
    -- Preview text for the list, taken from the newest message.
    snippet                varchar(320),
    last_message_at        timestamptz NOT NULL DEFAULT now(),
    -- INBOUND | OUTBOUND: whether we or the customer spoke last, which is what
    -- "waiting on us" filters actually mean.
    last_message_direction varchar(16) NOT NULL DEFAULT 'INBOUND',
    first_response_at      timestamptz,
    resolved_at            timestamptz,
    resolved_by            uuid,
    -- SLA state. sla_paused_ms accumulates time spent PENDING_CUSTOMER, which
    -- does not count against a first-response target.
    sla_policy_first_mins  integer,
    sla_due_at             timestamptz,
    sla_breached_at        timestamptz,
    sla_paused_at          timestamptz,
    sla_paused_ms          bigint      NOT NULL DEFAULT 0,
    spam_score             numeric(5, 2),
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_by             uuid,
    deleted_at             timestamptz,

    CONSTRAINT uq_mail_threads_reference UNIQUE (reference_key),
    CONSTRAINT ck_mail_threads_status CHECK (status IN
        ('OPEN', 'PENDING_CUSTOMER', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'SPAM', 'TRASH')),
    CONSTRAINT ck_mail_threads_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_mail_threads_direction
        CHECK (last_message_direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_mail_threads_counts
        CHECK (message_count >= 0 AND unread_count >= 0 AND sla_paused_ms >= 0),
    CONSTRAINT fk_mail_threads_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_threads_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_threads_assignee_user
        FOREIGN KEY (assignee_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_mail_threads_assignee_team
        FOREIGN KEY (assignee_team_id) REFERENCES teams (id) ON DELETE SET NULL,
    CONSTRAINT fk_mail_threads_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_mail_threads_resolved_by
        FOREIGN KEY (resolved_by) REFERENCES users (id) ON DELETE SET NULL
);

-- The default inbox view: one mailbox, open threads, newest activity first.
-- (last_message_at, id) is the keyset key, so this index alone serves a page.
CREATE INDEX ix_mail_threads_mailbox_listing
    ON mail_threads (mailbox_id, status, last_message_at DESC, id DESC)
    WHERE deleted_at IS NULL;
-- "Assigned to me" across every mailbox.
CREATE INDEX ix_mail_threads_assignee
    ON mail_threads (organization_id, assignee_user_id, status, last_message_at DESC)
    WHERE deleted_at IS NULL AND assignee_user_id IS NOT NULL;
CREATE INDEX ix_mail_threads_team
    ON mail_threads (organization_id, assignee_team_id, status, last_message_at DESC)
    WHERE deleted_at IS NULL AND assignee_team_id IS NOT NULL;
-- Unassigned queue, the most-watched view in any shared inbox.
CREATE INDEX ix_mail_threads_unassigned
    ON mail_threads (mailbox_id, last_message_at DESC)
    WHERE assignee_user_id IS NULL AND assignee_team_id IS NULL
      AND status = 'OPEN' AND deleted_at IS NULL;
-- Drives the SLA escalation sweep.
CREATE INDEX ix_mail_threads_sla_due
    ON mail_threads (sla_due_at)
    WHERE sla_due_at IS NOT NULL AND sla_breached_at IS NULL
      AND status IN ('OPEN', 'ON_HOLD') AND deleted_at IS NULL;
CREATE INDEX ix_mail_threads_customer
    ON mail_threads (organization_id, customer_email, last_message_at DESC);
-- Fallback threading probe: normalized subject within one mailbox.
CREATE INDEX ix_mail_threads_normalized_subject
    ON mail_threads (mailbox_id, normalized_subject, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_mail_threads_subject_trgm
    ON mail_threads USING gin (subject gin_trgm_ops);
CREATE INDEX ix_mail_threads_org ON mail_threads (organization_id);
CREATE INDEX ix_mail_threads_assigned_by ON mail_threads (assigned_by);
CREATE INDEX ix_mail_threads_resolved_by ON mail_threads (resolved_by);


-- -----------------------------------------------------------------------------
-- mail_messages: one email. Bodies live here; raw MIME and attachments go to
-- object storage.
--
-- Growth is the highest of any table. Kept unpartitioned for now so attachments
-- can hold a simple foreign key; V*__mail_messages_partition.sql documents the
-- switch to monthly range partitioning, which becomes worthwhile past roughly
-- 50M rows.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_messages (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    thread_id           uuid        NOT NULL,
    mailbox_id          uuid        NOT NULL,
    -- INBOUND | OUTBOUND
    direction           varchar(16) NOT NULL,
    -- RFC 5322 Message-ID. The idempotency key for ingestion.
    message_id_header   varchar(998),
    in_reply_to         varchar(998),
    -- Full References chain, used to attach a reply to the right thread.
    references_header   text,
    from_address        citext      NOT NULL,
    from_name           varchar(200),
    to_addresses        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    cc_addresses        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    bcc_addresses       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    reply_to_address    citext,
    subject             varchar(500),
    body_text           text,
    -- Sanitised HTML. The original is preserved in raw MIME, so re-sanitising
    -- after a filter change is always possible.
    body_html           text,
    snippet             varchar(320),
    -- Headers worth querying without fetching the raw message.
    headers             jsonb       NOT NULL DEFAULT '{}'::jsonb,
    -- Pointer to the untouched bytes in object storage.
    raw_file_id         uuid,
    size_bytes          integer,
    attachment_count    integer     NOT NULL DEFAULT 0,
    -- RECEIVED | QUEUED | SENDING | SENT | FAILED | BOUNCED | DRAFT
    delivery_status     varchar(16) NOT NULL DEFAULT 'RECEIVED',
    delivery_error      varchar(1000),
    spam_score          numeric(5, 2),
    spf_result          varchar(16),
    dkim_result         varchar(16),
    dmarc_result        varchar(16),
    -- Set for outbound messages an agent sent.
    sent_by_user_id     uuid,
    -- When the mail was actually accepted or received, which is not necessarily
    -- when we wrote the row.
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    read_at             timestamptz,
    -- Postgres full-text vector over subject and plain body.
    search_vector       tsvector GENERATED ALWAYS AS (
                            to_tsvector('simple',
                                coalesce(subject, '') || ' ' || coalesce(body_text, ''))
                        ) STORED,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted_at          timestamptz,

    CONSTRAINT ck_mail_messages_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_mail_messages_delivery CHECK (delivery_status IN
        ('RECEIVED', 'QUEUED', 'SENDING', 'SENT', 'FAILED', 'BOUNCED', 'DRAFT')),
    CONSTRAINT ck_mail_messages_counts
        CHECK (attachment_count >= 0 AND (size_bytes IS NULL OR size_bytes >= 0)),
    CONSTRAINT fk_mail_messages_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_messages_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_messages_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_messages_raw_file
        FOREIGN KEY (raw_file_id) REFERENCES stored_files (id) ON DELETE SET NULL,
    CONSTRAINT fk_mail_messages_sender
        FOREIGN KEY (sent_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- Ingestion idempotency: the same Message-ID delivered twice into the same
-- mailbox is one message. Partial because outbound drafts have no Message-ID yet.
CREATE UNIQUE INDEX uq_mail_messages_dedupe
    ON mail_messages (mailbox_id, message_id_header)
    WHERE message_id_header IS NOT NULL;

CREATE INDEX ix_mail_messages_thread
    ON mail_messages (thread_id, occurred_at, id) WHERE deleted_at IS NULL;
CREATE INDEX ix_mail_messages_mailbox
    ON mail_messages (mailbox_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_mail_messages_org
    ON mail_messages (organization_id, created_at DESC, id DESC);
CREATE INDEX ix_mail_messages_from
    ON mail_messages (organization_id, from_address, occurred_at DESC);
CREATE INDEX ix_mail_messages_in_reply_to
    ON mail_messages (in_reply_to) WHERE in_reply_to IS NOT NULL;
CREATE INDEX ix_mail_messages_search
    ON mail_messages USING gin (search_vector);
CREATE INDEX ix_mail_messages_sender ON mail_messages (sent_by_user_id);
CREATE INDEX ix_mail_messages_raw_file ON mail_messages (raw_file_id);

COMMENT ON INDEX uq_mail_messages_dedupe IS
    'Makes IMAP re-fetch and LMTP redelivery safe: ingestion can be replayed at will.';


-- -----------------------------------------------------------------------------
-- mail_attachments: metadata; bytes live in object storage via stored_files.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_attachments (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    message_id          uuid        NOT NULL,
    file_id             uuid        NOT NULL,
    filename            varchar(255) NOT NULL,
    content_type        varchar(160) NOT NULL,
    size_bytes          bigint      NOT NULL,
    -- True for images referenced by cid: in the HTML body rather than listed as
    -- a download.
    is_inline           boolean     NOT NULL DEFAULT false,
    content_id          varchar(255),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_mail_attachments_size CHECK (size_bytes >= 0),
    CONSTRAINT fk_mail_attachments_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_attachments_message
        FOREIGN KEY (message_id) REFERENCES mail_messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_attachments_file
        FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE RESTRICT
);

CREATE INDEX ix_mail_attachments_message ON mail_attachments (message_id);
CREATE INDEX ix_mail_attachments_file    ON mail_attachments (file_id);
CREATE INDEX ix_mail_attachments_org     ON mail_attachments (organization_id);
