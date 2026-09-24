-- Mail keeps the id of an organization, a person, a team, or a file, and stops promising
-- that the row is in this database.
--
-- Those foreign keys are what make mail inseparable from the oneOps tables. A message can
-- name a user who exists only as a uuid, which is the same way oneOps already names a person
-- in Identity. Constraints that stay inside schema mail, such as a message pointing at its
-- thread, are unchanged.
--
-- Deleting an organization or a user no longer deletes mail rows through the database. Nothing
-- in the application deletes an organization today. When that exists, it has to delete the mail
-- rows itself, or call the mail service.

ALTER TABLE mail.mail_canned_replies DROP CONSTRAINT IF EXISTS fk_canned_replies_organization;

ALTER TABLE mail.mail_delivery_events DROP CONSTRAINT IF EXISTS fk_delivery_events_organization;

ALTER TABLE mail.mail_inbound_raw DROP CONSTRAINT IF EXISTS fk_inbound_raw_file;
ALTER TABLE mail.mail_inbound_raw DROP CONSTRAINT IF EXISTS fk_inbound_raw_organization;

ALTER TABLE mail.mail_aliases DROP CONSTRAINT IF EXISTS fk_mail_aliases_organization;

ALTER TABLE mail.mail_attachments DROP CONSTRAINT IF EXISTS fk_mail_attachments_file;
ALTER TABLE mail.mail_attachments DROP CONSTRAINT IF EXISTS fk_mail_attachments_organization;

ALTER TABLE mail.mail_domains DROP CONSTRAINT IF EXISTS fk_mail_domains_organization;

ALTER TABLE mail.mail_mailboxes DROP CONSTRAINT IF EXISTS fk_mail_mailboxes_organization;
ALTER TABLE mail.mail_mailboxes DROP CONSTRAINT IF EXISTS mail_mailboxes_owner_user_id_fkey;

ALTER TABLE mail.mail_messages DROP CONSTRAINT IF EXISTS fk_mail_messages_organization;
ALTER TABLE mail.mail_messages DROP CONSTRAINT IF EXISTS fk_mail_messages_raw_file;
ALTER TABLE mail.mail_messages DROP CONSTRAINT IF EXISTS fk_mail_messages_sender;

ALTER TABLE mail.mail_outbox DROP CONSTRAINT IF EXISTS fk_mail_outbox_organization;

ALTER TABLE mail.mail_suppressions DROP CONSTRAINT IF EXISTS fk_mail_suppressions_organization;

ALTER TABLE mail.mail_tags DROP CONSTRAINT IF EXISTS fk_mail_tags_organization;

ALTER TABLE mail.mail_templates DROP CONSTRAINT IF EXISTS fk_mail_templates_organization;

ALTER TABLE mail.mail_thread_ai_suggestions DROP CONSTRAINT IF EXISTS fk_mail_thread_ai_suggestions_organization;

ALTER TABLE mail.mail_threads DROP CONSTRAINT IF EXISTS fk_mail_threads_assigned_by;
ALTER TABLE mail.mail_threads DROP CONSTRAINT IF EXISTS fk_mail_threads_assignee_team;
ALTER TABLE mail.mail_threads DROP CONSTRAINT IF EXISTS fk_mail_threads_assignee_user;
ALTER TABLE mail.mail_threads DROP CONSTRAINT IF EXISTS fk_mail_threads_organization;
ALTER TABLE mail.mail_threads DROP CONSTRAINT IF EXISTS fk_mail_threads_resolved_by;

ALTER TABLE mail.mail_webhook_events DROP CONSTRAINT IF EXISTS fk_mail_webhook_events_organization;

ALTER TABLE mail.mail_mailbox_members DROP CONSTRAINT IF EXISTS fk_mailbox_members_organization;
ALTER TABLE mail.mail_mailbox_members DROP CONSTRAINT IF EXISTS fk_mailbox_members_team;
ALTER TABLE mail.mail_mailbox_members DROP CONSTRAINT IF EXISTS fk_mailbox_members_user;

ALTER TABLE mail.mail_routing_rules DROP CONSTRAINT IF EXISTS fk_routing_rules_organization;

ALTER TABLE mail.mail_thread_drafts DROP CONSTRAINT IF EXISTS fk_thread_drafts_author;
ALTER TABLE mail.mail_thread_drafts DROP CONSTRAINT IF EXISTS fk_thread_drafts_organization;

ALTER TABLE mail.mail_thread_events DROP CONSTRAINT IF EXISTS fk_thread_events_actor;
ALTER TABLE mail.mail_thread_events DROP CONSTRAINT IF EXISTS fk_thread_events_organization;

ALTER TABLE mail.mail_thread_notes DROP CONSTRAINT IF EXISTS fk_thread_notes_author;
ALTER TABLE mail.mail_thread_notes DROP CONSTRAINT IF EXISTS fk_thread_notes_organization;

ALTER TABLE mail.mail_thread_tags DROP CONSTRAINT IF EXISTS fk_thread_tags_applied_by;
ALTER TABLE mail.mail_thread_tags DROP CONSTRAINT IF EXISTS fk_thread_tags_organization;

ALTER TABLE mail.mail_folders DROP CONSTRAINT IF EXISTS mail_folders_organization_id_fkey;

ALTER TABLE mail.mail_thread_flags DROP CONSTRAINT IF EXISTS mail_thread_flags_organization_id_fkey;
ALTER TABLE mail.mail_thread_flags DROP CONSTRAINT IF EXISTS mail_thread_flags_user_id_fkey;

ALTER TABLE mail.mail_thread_folders DROP CONSTRAINT IF EXISTS mail_thread_folders_moved_by_fkey;
ALTER TABLE mail.mail_thread_folders DROP CONSTRAINT IF EXISTS mail_thread_folders_organization_id_fkey;
