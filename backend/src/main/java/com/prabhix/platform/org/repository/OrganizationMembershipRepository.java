package com.prabhix.platform.org.repository;

import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    /**
     * Whether this pairing is a live membership, for the request-time tenant check.
     *
     * <p>An {@code exists} rather than a fetch because it runs on every request carrying
     * {@code X-Prabhix-Org} and nothing about the row itself is needed — the permissions come from
     * {@code PermissionResolver}, behind its own cache.
     */
    boolean existsByOrganizationIdAndUserIdAndStatus(
            UUID organizationId, UUID userId, MembershipStatus status);

    default boolean existsActiveMembership(UUID organizationId, UUID userId) {
        return existsByOrganizationIdAndUserIdAndStatus(
                organizationId, userId, MembershipStatus.ACTIVE);
    }

    List<OrganizationMembership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    List<OrganizationMembership> findByOrganizationIdAndUserIdIn(UUID organizationId, Set<UUID> userIds);

    long countByOrganizationIdAndStatus(UUID organizationId, MembershipStatus status);

    @Query("SELECT COUNT(m) FROM OrganizationMembership m WHERE m.organizationId = :orgId "
            + "AND m.roleId = :roleId AND m.status = 'ACTIVE'")
    long countActiveByRole(@Param("orgId") UUID orgId, @Param("roleId") UUID roleId);

    @Query(value = """
            SELECT * FROM organization_memberships m
            WHERE m.organization_id = :orgId
            AND (:status IS NULL OR m.status = CAST(:status AS varchar))
            AND (:roleId IS NULL OR m.role_id = CAST(:roleId AS uuid))
            AND (:department IS NULL OR m.department = :department)
            AND (:search IS NULL OR m.display_name ILIKE CONCAT('%', :search, '%')
                 OR m.email ILIKE CONCAT('%', :search, '%'))
            AND (
                CAST(:cursorCreatedAt AS timestamptz) IS NULL
                OR m.created_at < CAST(:cursorCreatedAt AS timestamptz)
                OR (m.created_at = CAST(:cursorCreatedAt AS timestamptz)
                    AND m.id < CAST(:cursorId AS uuid))
            )
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<OrganizationMembership> listMembersKeyset(
            @Param("orgId") UUID orgId,
            @Param("status") String status,
            @Param("roleId") UUID roleId,
            @Param("department") String department,
            @Param("search") String search,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
