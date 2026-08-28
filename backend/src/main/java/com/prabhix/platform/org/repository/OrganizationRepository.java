package com.prabhix.platform.org.repository;

import com.prabhix.platform.org.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(Organization.OrganizationStatus status);

    long countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(Instant since);

    /**
     * Tenant directory for the ops hub. Every other organization query is scoped to the caller's
     * memberships; this one deliberately is not, which is why it sits behind PLATFORM_ADMIN.
     */
    @Query(value = """
            SELECT * FROM organizations o
            WHERE o.deleted_at IS NULL
              AND (:status IS NULL OR o.status = :status)
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR o.created_at < CAST(:cursorAt AS timestamptz)
                OR (o.created_at = CAST(:cursorAt AS timestamptz)
                    AND o.id < CAST(:cursorId AS uuid))
              )
            ORDER BY o.created_at DESC, o.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Organization> listWithCursor(
            @Param("status") String status,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
