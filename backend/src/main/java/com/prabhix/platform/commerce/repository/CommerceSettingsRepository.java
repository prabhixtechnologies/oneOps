package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommerceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommerceSettingsRepository extends JpaRepository<CommerceSettings, UUID> {

    Optional<CommerceSettings> findByOrganizationId(UUID organizationId);
}
