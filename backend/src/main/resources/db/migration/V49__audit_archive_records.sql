-- =============================================================================
-- V49  Audit partition archive ledger
-- =============================================================================
-- One row per detached monthly partition. Written only after object storage
-- upload is verified, so a crash mid-job cannot lose compliance data silently.
-- -----------------------------------------------------------------------------

CREATE TABLE audit_archive_records (
    partition_name   varchar(64)  PRIMARY KEY,
    archived_at      timestamptz  NOT NULL DEFAULT now(),
    storage_bucket   varchar(120) NOT NULL,
    storage_keys     jsonb        NOT NULL,
    row_count        bigint       NOT NULL,
    content_sha256   varchar(64)  NOT NULL,
    detached_at      timestamptz,
    dropped_at       timestamptz,

    CONSTRAINT ck_audit_archive_records_row_count CHECK (row_count >= 0)
);

COMMENT ON TABLE audit_archive_records IS
    'Ledger of audit_logs partitions archived to object storage before detach/drop.';
