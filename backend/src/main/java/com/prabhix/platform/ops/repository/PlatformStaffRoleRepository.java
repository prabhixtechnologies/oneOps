package com.prabhix.platform.ops.repository;

import com.prabhix.platform.ops.domain.PlatformStaffRole;
import com.prabhix.platform.ops.domain.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformStaffRoleRepository extends JpaRepository<PlatformStaffRole, UUID> {

    List<PlatformStaffRole> findByUserIdAndRevokedAtIsNull(UUID userId);

    Optional<PlatformStaffRole> findByUserIdAndRoleAndRevokedAtIsNull(UUID userId, StaffRole role);

    /** Everyone who currently holds any staff role, for the console's staff list. */
    List<PlatformStaffRole> findByRevokedAtIsNullOrderByGrantedAtDesc();

    /**
     * How many people hold a role right now.
     *
     * <p>Used to refuse revoking the last OWNER. A platform with no owner has nobody who can grant the
     * role back, so recovering means editing the database by hand — during whatever incident prompted
     * the revocation.
     */
    long countByRoleAndRevokedAtIsNull(StaffRole role);
}
