-- =============================================================================
-- V15  Live chat: conversations, messages, canned replies, settings
-- =============================================================================

CREATE TABLE chat_settings (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    -- ONLINE | AWAY | OFFLINE
    availability            varchar(16) NOT NULL DEFAULT 'ONLINE',
    away_message            varchar(500),
    business_hours          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    pre_chat_enabled        boolean     NOT NULL DEFAULT true,
    offline_mailbox_id      uuid,
    transcript_template_key varchar(80) NOT NULL DEFAULT 'chat.transcript',
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_chat_settings_org UNIQUE (organization_id),
    CONSTRAINT ck_chat_settings_availability CHECK (availability IN ('ONLINE', 'AWAY', 'OFFLINE')),
    CONSTRAINT fk_chat_settings_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_settings_mailbox
        FOREIGN KEY (offline_mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE SET NULL
);


CREATE TABLE chat_conversations (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    visitor_id              uuid,
    customer_user_id        uuid,
    -- OPEN | PENDING | RESOLVED | CLOSED
    status                  varchar(16) NOT NULL DEFAULT 'OPEN',
    priority                varchar(16) NOT NULL DEFAULT 'NORMAL',
    subject                 varchar(500),
    visitor_name            varchar(160),
    visitor_email           citext,
    assigned_agent_id       uuid,
    tags                    jsonb       NOT NULL DEFAULT '[]'::jsonb,
    unread_agent_count      integer     NOT NULL DEFAULT 0,
    unread_visitor_count    integer     NOT NULL DEFAULT 0,
    last_message_at         timestamptz,
    last_message_preview    varchar(200),
    closed_at               timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT ck_chat_conversations_status CHECK (status IN ('OPEN', 'PENDING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_chat_conversations_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT fk_chat_conversations_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_conversations_visitor
        FOREIGN KEY (visitor_id) REFERENCES visitors (id) ON DELETE SET NULL,
    CONSTRAINT fk_chat_conversations_customer
        FOREIGN KEY (customer_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_chat_conversations_agent
        FOREIGN KEY (assigned_agent_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_chat_conversations_org_status
    ON chat_conversations (organization_id, status, last_message_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_chat_conversations_org_agent
    ON chat_conversations (organization_id, assigned_agent_id, status, last_message_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_chat_conversations_org_unassigned
    ON chat_conversations (organization_id, last_message_at DESC NULLS LAST)
    WHERE assigned_agent_id IS NULL AND deleted_at IS NULL AND status IN ('OPEN', 'PENDING');
CREATE INDEX ix_chat_conversations_visitor
    ON chat_conversations (visitor_id) WHERE visitor_id IS NOT NULL;


CREATE TABLE chat_messages (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    conversation_id         uuid        NOT NULL,
    -- VISITOR | AGENT | SYSTEM | NOTE
    sender_type             varchar(16) NOT NULL,
    sender_user_id          uuid,
    body                    text        NOT NULL,
    file_id                 uuid,
    occurred_at             timestamptz NOT NULL DEFAULT now(),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT ck_chat_messages_sender CHECK (sender_type IN ('VISITOR', 'AGENT', 'SYSTEM', 'NOTE')),
    CONSTRAINT fk_chat_messages_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_chat_messages_file
        FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE SET NULL
);

CREATE INDEX ix_chat_messages_conversation
    ON chat_messages (conversation_id, occurred_at ASC) WHERE deleted_at IS NULL;
CREATE INDEX ix_chat_messages_org_occurred
    ON chat_messages (organization_id, occurred_at DESC) WHERE deleted_at IS NULL;


CREATE TABLE chat_canned_replies (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    shortcut                varchar(60),
    title                   varchar(200) NOT NULL,
    body                    text        NOT NULL,
    usage_count             bigint      NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT fk_chat_canned_replies_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX ix_chat_canned_replies_org
    ON chat_canned_replies (organization_id) WHERE deleted_at IS NULL;
