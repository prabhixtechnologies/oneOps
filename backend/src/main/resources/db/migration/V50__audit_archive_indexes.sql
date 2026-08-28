-- Index for ops queries ("what was archived recently?").
CREATE INDEX ix_audit_archive_records_archived_at
    ON audit_archive_records (archived_at DESC);
