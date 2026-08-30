package com.prabhix.platform.files.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for an object in storage. The bytes themselves are never in Postgres.
 *
 * <p>Shared infrastructure: unlike a feature module's domain, any module may depend on this
 * one, because "a file" is not a concept that belongs to mail or billing in particular.
 */
@Getter
@Setter
@Entity
@Table(name = "stored_files")
public class StoredFile extends TenantScopedEntity {

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "bucket", nullable = false, length = 120)
    private String bucket;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** SHA-256 of the content, used to skip re-uploading an identical file. */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private FilePurpose purpose;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 16)
    private ScanStatus scanStatus = ScanStatus.PENDING;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public enum FilePurpose {
        MAIL_ATTACHMENT,
        MAIL_RAW_MIME,
        AVATAR,
        LOGO,
        EXPORT,
        IMPORT,
        INVOICE,
        CHAT_ATTACHMENT,
        /** A file somebody uploaded to the library, belonging to no other feature. */
        DOCUMENT
    }

    public enum ScanStatus {
        PENDING,
        CLEAN,
        INFECTED,
        /** Scanning is not configured, or the purpose is trusted (an invoice we generated). */
        SKIPPED
    }

    /** Attachments are only served once scanning has cleared them. */
    public boolean isDownloadable() {
        return deletedAt == null
                && (scanStatus == ScanStatus.CLEAN || scanStatus == ScanStatus.SKIPPED);
    }
}
