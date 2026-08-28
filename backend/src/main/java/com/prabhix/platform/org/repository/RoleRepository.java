package com.prabhix.platform.org.repository;

import com.prabhix.platform.org.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByOrganizationIdIsNullAndRoleKey(String roleKey);

    List<Role> findByOrganizationIdIsNullOrderByRankAsc();

    List<Role> findByOrganizationIdOrderByRankAsc(UUID organizationId);

    Optional<Role> findByOrganizationIdAndRoleKey(UUID organizationId, String roleKey);

    boolean existsByOrganizationIdAndRoleKey(UUID organizationId, String roleKey);
}
