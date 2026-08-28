package com.prabhix.platform.ai.repository;

import com.prabhix.platform.ai.domain.AiOrgSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiOrgSettingsRepository extends JpaRepository<AiOrgSettings, UUID> {

    Optional<AiOrgSettings> findByOrganizationId(UUID organizationId);
}
