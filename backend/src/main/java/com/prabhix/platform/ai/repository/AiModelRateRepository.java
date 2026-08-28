package com.prabhix.platform.ai.repository;

import com.prabhix.platform.ai.domain.AiModelRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiModelRateRepository extends JpaRepository<AiModelRate, UUID> {

    Optional<AiModelRate> findByProviderAndModel(String provider, String model);
}
