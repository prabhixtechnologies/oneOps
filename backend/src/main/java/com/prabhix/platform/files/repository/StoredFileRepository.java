package com.prabhix.platform.files.repository;

import com.prabhix.platform.files.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByBucketAndStorageKey(String bucket, String storageKey);

    @Query("""
            select f from StoredFile f
            where f.organizationId = :organizationId
              and f.checksumSha256 = :checksum
              and f.sizeBytes = :sizeBytes
              and f.deletedAt is null
            """)
    Optional<StoredFile> findDuplicate(@Param("organizationId") UUID organizationId,
                                       @Param("checksum") String checksum,
                                       @Param("sizeBytes") long sizeBytes);

    Optional<StoredFile> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    @Query(value = """
            SELECT * FROM stored_files f
            WHERE f.organization_id = :orgId
              AND f.deleted_at IS NULL
              AND (:purpose IS NULL OR f.purpose = :purpose)
              AND (:scanStatus IS NULL OR f.scan_status = :scanStatus)
              AND (:search IS NULL OR f.original_filename ILIKE CONCAT('%', :search, '%'))
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR f.created_at < CAST(:cursorAt AS timestamptz)
                OR (f.created_at = CAST(:cursorAt AS timestamptz)
                    AND f.id < CAST(:cursorId AS uuid))
              )
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<StoredFile> listWithCursor(
            @Param("orgId") UUID orgId,
            @Param("purpose") String purpose,
            @Param("scanStatus") String scanStatus,
            @Param("search") String search,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    @Query("""
            select f from StoredFile f
            where f.id in :ids and f.organizationId = :organizationId and f.deletedAt is null
            """)
    List<StoredFile> findAllByIdInAndOrganizationId(@Param("ids") List<UUID> ids,
                                                    @Param("organizationId") UUID organizationId);
}
