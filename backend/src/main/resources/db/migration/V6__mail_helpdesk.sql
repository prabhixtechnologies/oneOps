-- =============================================================================
-- V6  Helpdesk layer: tags, notes, activity, routing rules, canned replies,
--     raw inbound staging, thread presence
-- =============================================================================

-- -----------------------------------------------------------------------------
-- mail_tags / mail_thread_tags
-- -----------------------------------------------------------------------------
CREATE TABLE mail_tags (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    slug            varchar(60) NOT NULL,
    name            varchar(60) NOT NULL,
    colour          varchar(9)  NOT NULL DEFAULT '#7C3AED',
    description     varchar(255),
    -- Denormalised so the tag rail can show counts without aggregating.
    usage_count     integer     NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_mail_tags_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_mail_tags_usage CHECK (usage_count >= 0),
    CONSTRAINT fk_mail_tags_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX ix_mail_tags_org ON mail_tags (organization_id);


CREATE TABLE mail_thread_tags (
    thread_id       uuid        NOT NULL,
    tag_id          uuid        NOT NULL,
    organization_id uuid        NOT NULL,
    applied_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_mail_thread_tags PRIMARY KEY (thread_id, tag_id),
    CONSTRAINT fk_thread_tags_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_tags_tag
        FOREIGN KEY (tag_id) REFERENCES mail_tags (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_tags_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_tags_applied_by
        FOREIGN KEY (applied_by) REFERENCES users (id) ON DELETE SET NULL
);

-- Reverse lookup: every thread carrying a tag, newest first.
CREATE INDEX ix_thread_tags_tag ON mail_thread_tags (tag_id, created_at DESC);
CREATE INDEX ix_thread_tags_org ON mail_thread_tags (organization_id);
CREATE INDEX ix_thread_tags_applied_by ON mail_thread_tags (applied_by);


-- -----------------------------------------------------------------------------
-- mail_thread_notes: internal only. Never included in an outbound message.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_thread_notes (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    thread_id       uuid        NOT NULL,
    author_user_id  uuid        NOT NULL,
    body_html       text        NOT NULL,
    body_text       text,
    -- Users @-mentioned in the note, who get notified.
    mentioned_users jsonb       NOT NULL DEFAULT '[]'::jsonb,
    edited_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,

    CONSTRAINT fk_thread_notes_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_notes_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_notes_author
        FOREIGN KEY (author_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_thread_notes_thread ON mail_thread_notes (thread_id, created_at)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_thread_notes_author ON mail_thread_notes (author_user_id);
CREATE INDEX ix_thread_notes_org    ON mail_thread_notes (organization_id);

COMMENT ON TABLE mail_thread_notes IS
    'Internal collaboration. MailSendService must never read from this table.';


-- -----------------------------------------------------------------------------
-- mail_thread_events: the activity timeline shown beside a thread. Append-only.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_thread_events (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid        NOT NULL,
    thread_id       uuid        NOT NULL,
    -- CREATED | MESSAGE_RECEIVED | MESSAGE_SENT | ASSIGNED | UNASSIGNED |
    -- STATUS_CHANGED | PRIORITY_CHANGED | TAG_ADDED | TAG_REMOVED |
    -- NOTE_ADDED | SLA_BREACHED | MERGED | MOVED | AUTO_REPLIED | RULE_APPLIED
    event_type      varchar(32) NOT NULL,
    actor_user_id   uuid,
    -- Null actor means the platform did it: a routing rule or an SLA sweep.
    actor_label     varchar(160),
    from_value      varchar(255),
    to_value        varchar(255),
    metadata        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_thread_events_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_events_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_events_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_thread_events_thread ON mail_thread_events (thread_id, created_at);
CREATE INDEX ix_thread_events_org    ON mail_thread_events (organization_id, created_at DESC);
CREATE INDEX ix_thread_events_actor  ON mail_thread_events (actor_user_id);


-- -----------------------------------------------------------------------------
-- mail_routing_rules: declarative inbound routing, evaluated in priority order.
--
-- Conditions and actions are jsonb rather than normalised tables: the shape is
-- driven by a UI builder, changes often, and is always read as a whole document.
-- The schema is documented in docs/MAIL.md.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_routing_rules (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    -- Null applies the rule to every mailbox in the organization.
    mailbox_id      uuid,
    name            varchar(160) NOT NULL,
    description     varchar(500),
    enabled         boolean     NOT NULL DEFAULT true,
    -- Lowest number evaluated first.
    priority        integer     NOT NULL DEFAULT 100,
    -- ALL | ANY
    match_mode      varchar(8)  NOT NULL DEFAULT 'ALL',
    conditions      jsonb       NOT NULL DEFAULT '[]'::jsonb,
    actions         jsonb       NOT NULL DEFAULT '[]'::jsonb,
    -- When false, a match stops evaluation. When true, later rules still run.
    continue_after_match boolean NOT NULL DEFAULT false,
    match_count     bigint      NOT NULL DEFAULT 0,
    last_matched_at timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT ck_routing_rules_match_mode CHECK (match_mode IN ('ALL', 'ANY')),
    CONSTRAINT ck_routing_rules_priority CHECK (priority >= 0),
    CONSTRAINT ck_routing_rules_conditions_array CHECK (jsonb_typeof(conditions) = 'array'),
    CONSTRAINT ck_routing_rules_actions_array CHECK (jsonb_typeof(actions) = 'array'),
    CONSTRAINT fk_routing_rules_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_routing_rules_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE CASCADE
);

CREATE INDEX ix_routing_rules_lookup
    ON mail_routing_rules (organization_id, mailbox_id, priority)
    WHERE enabled = true;


-- -----------------------------------------------------------------------------
-- mail_canned_replies: reusable answers, optionally scoped to one mailbox.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_canned_replies (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    mailbox_id      uuid,
    -- Typed shortcut, e.g. "/refund".
    shortcut        varchar(60),
    title           varchar(200) NOT NULL,
    subject         varchar(500),
    body_html       text        NOT NULL,
    body_text       text,
    -- Placeholder names the body expects, e.g. customer.name.
    variables       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    usage_count     bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,

    CONSTRAINT fk_canned_replies_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_canned_replies_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE CASCADE
);

CREATE INDEX ix_canned_replies_org ON mail_canned_replies (organization_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_canned_replies_mailbox ON mail_canned_replies (mailbox_id);
CREATE UNIQUE INDEX uq_canned_replies_shortcut
    ON mail_canned_replies (organization_id, shortcut)
    WHERE shortcut IS NOT NULL AND deleted_at IS NULL;


-- -----------------------------------------------------------------------------
-- mail_inbound_raw: staging for every message we accept, before parsing.
--
-- Keeping the original bytes means a parser bug is recoverable: fix the parser,
-- reset status to PENDING, and replay. Without this, malformed MIME is data loss.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_inbound_raw (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid        NOT NULL,
    mailbox_id          uuid        NOT NULL,
    -- IMAP | LMTP | WEBHOOK | MANUAL
    source              varchar(16) NOT NULL,
    -- IMAP UID this came from, so a fetch can be resumed exactly.
    source_uid          bigint,
    message_id_header   varchar(998),
    raw_file_id         uuid,
    -- Inline copy for messages small enough that a storage round trip is not
    -- worth it. Larger messages set raw_file_id instead.
    raw_content         text,
    size_bytes          integer,
    -- PENDING | PROCESSING | PROCESSED | FAILED | SKIPPED_DUPLICATE
    status              varchar(24) NOT NULL DEFAULT 'PENDING',
    attempts            integer     NOT NULL DEFAULT 0,
    last_error          varchar(2000),
    -- Set once parsing succeeds, linking staging to the parsed row.
    resulting_message_id uuid,
    received_at         timestamptz NOT NULL DEFAULT now(),
    processed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_inbound_raw_source
        CHECK (source IN ('IMAP', 'LMTP', 'WEBHOOK', 'MANUAL')),
    CONSTRAINT ck_inbound_raw_status CHECK (status IN
        ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED', 'SKIPPED_DUPLICATE')),
    CONSTRAINT ck_inbound_raw_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_inbound_raw_has_payload
        CHECK (raw_file_id IS NOT NULL OR raw_content IS NOT NULL),
    CONSTRAINT fk_inbound_raw_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_inbound_raw_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE CASCADE,
    CONSTRAINT fk_inbound_raw_file
        FOREIGN KEY (raw_file_id) REFERENCES stored_files (id) ON DELETE SET NULL,
    CONSTRAINT fk_inbound_raw_message
        FOREIGN KEY (resulting_message_id) REFERENCES mail_messages (id) ON DELETE SET NULL
);

-- The worker claim query: pending rows oldest first, taken with
-- FOR UPDATE SKIP LOCKED so any number of workers can run concurrently.
CREATE INDEX ix_inbound_raw_pending
    ON mail_inbound_raw (received_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_inbound_raw_mailbox ON mail_inbound_raw (mailbox_id, source_uid DESC);
CREATE INDEX ix_inbound_raw_org     ON mail_inbound_raw (organization_id, received_at DESC);
CREATE INDEX ix_inbound_raw_failed  ON mail_inbound_raw (mailbox_id, received_at DESC)
    WHERE status = 'FAILED';
CREATE INDEX ix_inbound_raw_file    ON mail_inbound_raw (raw_file_id);
CREATE INDEX ix_inbound_raw_message ON mail_inbound_raw (resulting_message_id);
-- Same UID fetched twice from the same mailbox is the same delivery.
CREATE UNIQUE INDEX uq_inbound_raw_imap_uid
    ON mail_inbound_raw (mailbox_id, source_uid)
    WHERE source = 'IMAP' AND source_uid IS NOT NULL;


-- -----------------------------------------------------------------------------
-- mail_thread_drafts: an agent's unsent reply.
--
-- Persisted rather than kept in browser state so a draft survives a refresh and,
-- more importantly, so every agent can see that someone is already replying.
-- One live draft per thread per author.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_thread_drafts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL,
    thread_id       uuid        NOT NULL,
    author_user_id  uuid        NOT NULL,
    -- REPLY | REPLY_ALL | FORWARD
    reply_mode      varchar(16) NOT NULL DEFAULT 'REPLY',
    to_addresses    jsonb       NOT NULL DEFAULT '[]'::jsonb,
    cc_addresses    jsonb       NOT NULL DEFAULT '[]'::jsonb,
    bcc_addresses   jsonb       NOT NULL DEFAULT '[]'::jsonb,
    subject         varchar(500),
    body_html       text,
    attachment_ids  jsonb       NOT NULL DEFAULT '[]'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT uq_thread_drafts_thread_author UNIQUE (thread_id, author_user_id),
    CONSTRAINT ck_thread_drafts_mode
        CHECK (reply_mode IN ('REPLY', 'REPLY_ALL', 'FORWARD')),
    CONSTRAINT fk_thread_drafts_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_drafts_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_drafts_author
        FOREIGN KEY (author_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_thread_drafts_thread ON mail_thread_drafts (thread_id);
CREATE INDEX ix_thread_drafts_author ON mail_thread_drafts (author_user_id, updated_at DESC);
CREATE INDEX ix_thread_drafts_org    ON mail_thread_drafts (organization_id);
