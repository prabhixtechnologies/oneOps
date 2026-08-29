-- A password Dovecot can verify, so IMAP and SMTP authentication stop depending on a file.
--
-- Dovecot currently authenticates against dovecot/bootstrap.passwd, a checked-in passwd-file that
-- has to be edited and the container restarted for every mailbox. The SQL passdb it should be using
-- was already written and commented out, waiting on this column: mail_mailboxes had nowhere to keep
-- a hash Dovecot could read.
--
-- Not reusable for this: imap_password_enc and smtp_password_enc hold AES-GCM ciphertext of the
-- credentials for *pulling* from someone else's IMAP server in EXTERNAL_IMAP mode. They are
-- reversible by design, because the poller has to present the original password, and Dovecot has no
-- way to decrypt them.
--
-- Not reusable either: users.password_hash. A mailbox is not a user — a shared mailbox has many
-- members and no single owner, and an IMAP client stores this password on the device in a form it
-- can replay, so it must be revocable without affecting the ability to sign in to the console.
ALTER TABLE mail_mailboxes
    -- BCrypt, which Dovecot reads as BLF-CRYPT. Wide enough for the $2b$ prefix plus a scheme prefix
    -- if a future migration ever needs to disambiguate, e.g. '{SHA512-CRYPT}'.
    ADD COLUMN IF NOT EXISTS password_hash       text,
    -- Shown in the console as "mail password set on ...", and the only way to tell a mailbox that
    -- has never had one from a mailbox whose password was deliberately revoked.
    ADD COLUMN IF NOT EXISTS password_updated_at timestamptz;

COMMENT ON COLUMN mail_mailboxes.password_hash IS
    'BCrypt hash of the mailbox IMAP/SMTP password, read by Dovecot''s SQL passdb as BLF-CRYPT. '
        'Null means the mailbox cannot be logged into by a mail client. Distinct from '
        'imap_password_enc, which is a reversible credential for pulling from an external server.';

-- Partial, because the only query that uses it is Dovecot's passdb lookup, which always filters to
-- an active mailbox with a password set. The full index would be almost entirely nulls for
-- deployments in EXTERNAL_IMAP mode, which is every deployment today.
CREATE INDEX IF NOT EXISTS idx_mail_mailboxes_passdb
    ON mail_mailboxes (address)
    WHERE password_hash IS NOT NULL
        AND status = 'ACTIVE'
        AND deleted_at IS NULL;
