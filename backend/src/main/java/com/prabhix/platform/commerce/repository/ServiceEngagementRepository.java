package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.ServiceEngagement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceEngagementRepository extends JpaRepository<ServiceEngagement, UUID> {

    Optional<ServiceEngagement> findByOrderItemId(UUID orderItemId);
}
