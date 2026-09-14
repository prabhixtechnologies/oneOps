-- PERSONAL mailboxes must name an owner and that owner must be a member.
--
-- owner_user_id was added without a write path: create() stored kind=PERSONAL and left the column
-- null, and seed wrote the owner but never a mail_mailbox_members row. Mailroom's "My mail" view
-- is membership (plus owner), so those boxes were invisible to the person they belong to.

UPDATE mail.mail_mailboxes mb
SET owner_user_id = COALESCE(
        mb.owner_user_id,
        (
            SELECT m.user_id
            FROM mail.mail_mailbox_members m
            WHERE m.mailbox_id = mb.id
              AND m.user_id IS NOT NULL
            ORDER BY m.created_at ASC, m.id ASC
            LIMIT 1
        ),
        mb.created_by
    )
WHERE mb.kind = 'PERSONAL'
  AND mb.deleted_at IS NULL
  AND mb.owner_user_id IS NULL;

INSERT INTO mail.mail_mailbox_members (
    organization_id, mailbox_id, user_id, access_level, notify
)
SELECT mb.organization_id, mb.id, mb.owner_user_id, 'LEAD', true
FROM mail.mail_mailboxes mb
WHERE mb.kind = 'PERSONAL'
  AND mb.deleted_at IS NULL
  AND mb.owner_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM mail.mail_mailbox_members x
      WHERE x.mailbox_id = mb.id
        AND x.user_id = mb.owner_user_id
  );
