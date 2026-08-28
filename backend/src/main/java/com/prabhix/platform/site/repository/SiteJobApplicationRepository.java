package com.prabhix.platform.site.repository;

import com.prabhix.platform.site.domain.SiteJobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteJobApplicationRepository extends JpaRepository<SiteJobApplication, UUID> {

    Optional<SiteJobApplication> findByRoleSlugAndEmail(String roleSlug, String email);

    @Query(value = """
            SELECT * FROM site_job_applications a
            WHERE (:status IS NULL OR a.status = :status)
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR a.created_at < CAST(:cursorAt AS timestamptz)
                OR (a.created_at = CAST(:cursorAt AS timestamptz)
                    AND a.id < CAST(:cursorId AS uuid))
              )
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<SiteJobApplication> listWithCursor(
            @Param("status") String status,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
