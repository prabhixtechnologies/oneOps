package com.prabhix.platform.org.repository;

import com.prabhix.platform.org.domain.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    /**
     * The teams a person belongs to, within one organization.
     *
     * <p>Scoped by organization as well as user even though a membership row implies both, because
     * this feeds mailbox access resolution and an access query that trusts a single foreign key to
     * carry tenancy is the kind that leaks across tenants after an unrelated refactor.
     */
    @Query("SELECT tm.teamId FROM TeamMember tm WHERE tm.organizationId = :orgId AND tm.userId = :userId")
    List<UUID> findTeamIdsByUser(@Param("orgId") UUID orgId, @Param("userId") UUID userId);

    List<TeamMember> findByTeamId(UUID teamId);

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    long countByTeamId(UUID teamId);
}
