package com.prabhix.platform.flags.repository;

import com.prabhix.platform.flags.domain.FeatureFlagOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagOverrideRepository extends JpaRepository<FeatureFlagOverride, UUID> {

    List<FeatureFlagOverride> findByOrganizationId(UUID organizationId);

    Optional<FeatureFlagOverride> findByOrganizationIdAndFlagKey(UUID organizationId, String flagKey);
}
