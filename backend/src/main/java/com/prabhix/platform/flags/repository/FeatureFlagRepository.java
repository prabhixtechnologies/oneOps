package com.prabhix.platform.flags.repository;

import com.prabhix.platform.flags.domain.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    List<FeatureFlag> findAllByOrderByFlagKeyAsc();

    Optional<FeatureFlag> findByFlagKey(String flagKey);
}
