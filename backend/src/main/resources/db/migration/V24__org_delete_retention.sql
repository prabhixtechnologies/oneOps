-- =============================================================================
-- V24  Organization soft-delete retention and chat file purpose
-- =============================================================================

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS purge_scheduled_at timestamptz;

COMMENT ON COLUMN organizations.purge_scheduled_at IS
    'When a hard purge may run after soft-delete. Null while the organization is active.';

ALTER TABLE stored_files DROP CONSTRAINT IF EXISTS ck_stored_files_purpose;
ALTER TABLE stored_files
    ADD CONSTRAINT ck_stored_files_purpose CHECK (purpose IN
        ('MAIL_ATTACHMENT', 'MAIL_RAW_MIME', 'AVATAR', 'LOGO', 'EXPORT', 'IMPORT', 'INVOICE',
         'CHAT_ATTACHMENT'));
