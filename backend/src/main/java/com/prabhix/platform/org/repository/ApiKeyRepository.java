package com.prabhix.platform.org.repository;

import com.prabhix.platform.org.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByOrganizationIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    Optional<ApiKey> findByIdAndOrganizationIdAndRevokedAtIsNull(UUID id, UUID organizationId);

    Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);
}
