-- Turns the helpdesk into something a person can use as their own mail.
--
-- Today a thread has a helpdesk *status* — OPEN, PENDING_CUSTOMER, RESOLVED — and that is the only
-- place it can be. That is the right model for a support queue and the wrong one for a mailbox: nobody
-- resolves a message from their accountant, and there is no way to put it somewhere. Folders are not a
-- status, because a thread's status is about the work and its folder is about where the person filed it,
-- and the two change independently.
--
-- Three things are added, and nothing existing changes shape:
--
--   * folders, per mailbox, with a small fixed set created automatically and user folders alongside them
--   * per-person flags, because "read" and "starred" are facts about a reader and not about a thread —
--     a shared mailbox has several readers and one unread_count cannot be true for all of them
--   * an owner for a personal mailbox, so "my mail" is answerable without reading the membership table
--     and guessing

-- ---------------------------------------------------------------------------------------------------
-- Folders
-- ---------------------------------------------------------------------------------------------------

CREATE TABLE mail_folders
(
    id              uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    mailbox_id      uuid        NOT NULL REFERENCES mail_mailboxes (id) ON DELETE CASCADE,

    -- INBOX | SENT | DRAFTS | ARCHIVE | TRASH | SPAM | CUSTOM
    --
    -- The system kinds exist so that code can find "the trash folder for this mailbox" without matching
    -- on a name a user is free to rename or translate. CUSTOM is everything a person made.
    kind            varchar(16) NOT NULL DEFAULT 'CUSTOM',
    name            varchar(120) NOT NULL,
    -- Nesting, one level of parent per row. Null for a top-level folder.
    parent_id       uuid REFERENCES mail_folders (id) ON DELETE CASCADE,
    sort_order      integer     NOT NULL DEFAULT 100,
    colour          varchar(9),

    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,

    CONSTRAINT ck_mail_folders_kind CHECK (kind IN
        ('INBOX', 'SENT', 'DRAFTS', 'ARCHIVE', 'TRASH', 'SPAM', 'CUSTOM')),
    -- A folder cannot be its own parent. Deeper cycles are prevented in the service, which is where the
    -- ancestor walk lives; this catches the single case worth a constraint.
    CONSTRAINT ck_mail_folders_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

-- One INBOX per mailbox, one TRASH, and so on. Partial on kind so a mailbox may have any number of
-- CUSTOM folders while the system ones stay singular — without this, two INBOX rows would make
-- "the inbox" a question with two answers.
CREATE UNIQUE INDEX uq_mail_folders_system_kind
    ON mail_folders (mailbox_id, kind)
    WHERE kind <> 'CUSTOM' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_mail_folders_name
    ON mail_folders (mailbox_id, coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_mail_folders_mailbox ON mail_folders (mailbox_id) WHERE deleted_at IS NULL;

-- Where a thread is filed. One row per thread, not a many-to-many: IMAP semantics, and the thing a
-- person means by "move to Archive" is that it is no longer in the inbox.
CREATE TABLE mail_thread_folders
(
    thread_id       uuid PRIMARY KEY REFERENCES mail_threads (id) ON DELETE CASCADE,
    organization_id uuid        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    folder_id       uuid        NOT NULL REFERENCES mail_folders (id) ON DELETE CASCADE,
    moved_at        timestamptz NOT NULL DEFAULT now(),
    moved_by        uuid REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_mail_thread_folders_folder ON mail_thread_folders (folder_id, moved_at DESC);

-- Backfills the system folders for every mailbox that already exists, so nothing has to special-case a
-- mailbox created before this migration. New mailboxes get theirs from MailFolderService.
INSERT INTO mail_folders (organization_id, mailbox_id, kind, name, sort_order)
SELECT m.organization_id, m.id, f.kind, f.name, f.sort_order
FROM mail_mailboxes m
         CROSS JOIN (VALUES ('INBOX', 'Inbox', 10),
                            ('SENT', 'Sent', 20),
                            ('DRAFTS', 'Drafts', 30),
                            ('ARCHIVE', 'Archive', 40),
                            ('SPAM', 'Spam', 50),
                            ('TRASH', 'Trash', 60)) AS f(kind, name, sort_order)
WHERE m.deleted_at IS NULL;

-- Existing threads land in the folder their helpdesk status implies. SPAM and TRASH are the only two
-- statuses that carry a location; everything else is in the inbox regardless of whether the work is
-- finished, because a resolved ticket is still a thread you can find.
INSERT INTO mail_thread_folders (thread_id, organization_id, folder_id)
SELECT t.id,
       t.organization_id,
       (SELECT f.id
        FROM mail_folders f
        WHERE f.mailbox_id = t.mailbox_id
          AND f.kind = CASE t.status WHEN 'SPAM' THEN 'SPAM' WHEN 'TRASH' THEN 'TRASH' ELSE 'INBOX' END
        LIMIT 1)
FROM mail_threads t
WHERE t.deleted_at IS NULL
ON CONFLICT (thread_id) DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- Per-reader flags
-- ---------------------------------------------------------------------------------------------------

-- "Read" and "starred" are facts about a reader, not about a thread. A shared mailbox has several
-- readers, and mail_threads.unread_count cannot be true for all of them at once — it stays as the
-- mailbox-level summary the helpdesk list uses, and this is what a mail client asks.
CREATE TABLE mail_thread_flags
(
    thread_id       uuid        NOT NULL REFERENCES mail_threads (id) ON DELETE CASCADE,
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    organization_id uuid        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    read_at         timestamptz,
    starred_at      timestamptz,
    -- Comes back to the inbox at this time. Null means never, which is almost all of them.
    snoozed_until   timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (thread_id, user_id)
);

-- The starred view, and the snooze sweep. Both are small slices of a large table, so both are partial.
CREATE INDEX ix_mail_thread_flags_starred
    ON mail_thread_flags (user_id, starred_at DESC) WHERE starred_at IS NOT NULL;
CREATE INDEX ix_mail_thread_flags_snoozed
    ON mail_thread_flags (snoozed_until) WHERE snoozed_until IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------
-- Whose mailbox it is
-- ---------------------------------------------------------------------------------------------------

-- kind = 'PERSONAL' has existed since V5 and has never meant anything at runtime: access is decided by
-- mail_mailbox_members, so a personal mailbox is a shared one with a single member by convention. That
-- makes "show me my mail" a question answered by counting members and hoping.
ALTER TABLE mail_mailboxes
    ADD COLUMN IF NOT EXISTS owner_user_id uuid REFERENCES users (id) ON DELETE SET NULL;

-- Deliberately not unique: somebody may have both their own address and a second personal one, and
-- refusing that would be a rule invented by this migration rather than by anybody's requirements.
CREATE INDEX ix_mail_mailboxes_owner
    ON mail_mailboxes (owner_user_id) WHERE owner_user_id IS NOT NULL AND deleted_at IS NULL;

-- Fills in the owner for personal mailboxes that already have exactly one member. One member is
-- unambiguous; anything else is a guess, and a wrong guess here hands one person's mail to another.
UPDATE mail_mailboxes m
SET owner_user_id = (SELECT mm.user_id
                     FROM mail_mailbox_members mm
                     WHERE mm.mailbox_id = m.id AND mm.user_id IS NOT NULL
                     LIMIT 1)
WHERE m.kind = 'PERSONAL'
  AND m.owner_user_id IS NULL
  AND m.deleted_at IS NULL
  AND (SELECT count(*) FROM mail_mailbox_members mm
       WHERE mm.mailbox_id = m.id AND mm.user_id IS NOT NULL) = 1;

-- ---------------------------------------------------------------------------------------------------
-- Standalone drafts
-- ---------------------------------------------------------------------------------------------------

-- mail_thread_drafts requires a thread, which makes composing a new message impossible to save: there
-- is nothing to attach the draft to until the mail has been sent. Nullable thread_id fixes that, and
-- the unique constraint has to change with it — one draft per thread per author was the rule, and a
-- person may have any number of unsent new messages.
ALTER TABLE mail_thread_drafts
    ALTER COLUMN thread_id DROP NOT NULL;

ALTER TABLE mail_thread_drafts
    ADD COLUMN IF NOT EXISTS mailbox_id uuid REFERENCES mail_mailboxes (id) ON DELETE CASCADE;

ALTER TABLE mail_thread_drafts
    DROP CONSTRAINT IF EXISTS uq_thread_drafts_thread_author;

-- Still one reply draft per thread per author, since that is what the compose box in a thread is. Now
-- partial, so the standalone drafts — thread_id null — are not forced to collide with each other.
CREATE UNIQUE INDEX uq_thread_drafts_thread_author
    ON mail_thread_drafts (thread_id, author_user_id) WHERE thread_id IS NOT NULL;

CREATE INDEX ix_thread_drafts_author
    ON mail_thread_drafts (author_user_id, updated_at DESC);
