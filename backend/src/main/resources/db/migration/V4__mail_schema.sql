-- Move the mail module's tables into a schema of their own.
--
-- Mail is becoming its own service. Today it is 24 tables sitting in public alongside everything
-- else, reachable by any query that knows a table name, and the only thing keeping the rest of the
-- backend out of them is that nobody has written the query yet. A schema turns that into something
-- the database enforces: one object to grant on, and a name that has to be said out loud to cross.
--
-- What does not move: mail_requests. It is the platform's outbox of "please send this", written by
-- billing, auth, chat, commerce and site inside their own transactions. It belongs to the side that
-- asks for mail, not the side that sends it, and it has to keep working when mail is a service
-- somewhere else.
--
-- Foreign keys out of mail -- to organizations, users, teams, stored_files -- are kept. They work
-- across schemas unchanged, and they are the reason the mail role needs REFERENCES rather than
-- SELECT on those four tables. Referential integrity survives the move; access does not.

CREATE SCHEMA IF NOT EXISTS mail;

COMMENT ON SCHEMA mail IS
    'Mail module tables. Owned by the mail role; the platform role has no rights here.';

-- Two foreign keys pointed the wrong way: platform tables into mail.
--
-- They have to go, and not because of the schema move -- that would survive them. A service cannot
-- be extracted while another module''s inserts fail on its rows, and neither of these was buying
-- integrity worth that. Both are nullable, both were ON DELETE SET NULL, and both are read through
-- code that already treats a missing target as "no mail": chat calls MailboxDirectory.addressOf and
-- gets an empty Optional, and nothing in the backend writes site_leads.thread_id at all.
--
-- The columns stay. They are how chat finds its offline mailbox and how a lead is tied to a thread;
-- only the database''s promise that the target exists is being given up, which is the promise no
-- cross-service reference can make anyway.
ALTER TABLE public.chat_settings DROP CONSTRAINT IF EXISTS fk_chat_settings_mailbox;
ALTER TABLE public.site_leads DROP CONSTRAINT IF EXISTS fk_site_leads_thread;

COMMENT ON COLUMN public.chat_settings.offline_mailbox_id IS
    'Mailbox in the mail service. No foreign key: resolved through MailboxDirectory, which returns '
    'empty when the mailbox is gone, and offline routing is skipped rather than failed.';
COMMENT ON COLUMN public.site_leads.thread_id IS
    'Thread in the mail service, if this lead came from one. No foreign key across the boundary.';

-- Indexes, constraints, sequences and comments follow their table.
ALTER TABLE public.mail_aliases SET SCHEMA mail;
ALTER TABLE public.mail_attachments SET SCHEMA mail;
ALTER TABLE public.mail_canned_replies SET SCHEMA mail;
ALTER TABLE public.mail_delivery_events SET SCHEMA mail;
ALTER TABLE public.mail_domains SET SCHEMA mail;
ALTER TABLE public.mail_folders SET SCHEMA mail;
ALTER TABLE public.mail_inbound_raw SET SCHEMA mail;
ALTER TABLE public.mail_mailbox_members SET SCHEMA mail;
ALTER TABLE public.mail_mailboxes SET SCHEMA mail;
ALTER TABLE public.mail_messages SET SCHEMA mail;
ALTER TABLE public.mail_outbox SET SCHEMA mail;
ALTER TABLE public.mail_routing_rules SET SCHEMA mail;
ALTER TABLE public.mail_suppressions SET SCHEMA mail;
ALTER TABLE public.mail_tags SET SCHEMA mail;
ALTER TABLE public.mail_templates SET SCHEMA mail;
ALTER TABLE public.mail_thread_ai_suggestions SET SCHEMA mail;
ALTER TABLE public.mail_thread_drafts SET SCHEMA mail;
ALTER TABLE public.mail_thread_events SET SCHEMA mail;
ALTER TABLE public.mail_thread_flags SET SCHEMA mail;
ALTER TABLE public.mail_thread_folders SET SCHEMA mail;
ALTER TABLE public.mail_thread_notes SET SCHEMA mail;
ALTER TABLE public.mail_thread_tags SET SCHEMA mail;
ALTER TABLE public.mail_threads SET SCHEMA mail;
ALTER TABLE public.mail_webhook_events SET SCHEMA mail;

-- Every mail table, and no others. A table added to public later that happens to start with "mail_"
-- is a mistake worth failing the deploy over, and so is one of these left behind.
DO $$
DECLARE
    stranded text;
    moved integer;
BEGIN
    SELECT string_agg(tablename, ', ' ORDER BY tablename) INTO stranded
    FROM pg_tables
    WHERE schemaname = 'public' AND tablename LIKE 'mail\_%' AND tablename <> 'mail_requests';

    IF stranded IS NOT NULL THEN
        RAISE EXCEPTION 'Mail tables left in public: %', stranded;
    END IF;

    SELECT count(*) INTO moved FROM pg_tables WHERE schemaname = 'mail';
    IF moved <> 24 THEN
        RAISE EXCEPTION 'Expected 24 tables in the mail schema, found %', moved;
    END IF;
END $$;

-- The role itself is not created here. Roles are cluster-wide, this migration runs as the
-- application user, and on RDS that user cannot CREATE ROLE. Infra/deploy/aws/mail-role.sql does it,
-- and Infra/deploy/RUNBOOK-rds.md says when. Until then the platform user still owns this schema and
-- reaches through it, which is what lets the mail module keep running in-process.
