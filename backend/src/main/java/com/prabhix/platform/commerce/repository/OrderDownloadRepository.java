package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.OrderDownload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderDownloadRepository extends JpaRepository<OrderDownload, UUID> {

    Optional<OrderDownload> findByDownloadToken(String downloadToken);

    List<OrderDownload> findByOrderIdAndOrganizationId(UUID orderId, UUID organizationId);

    Optional<OrderDownload> findByOrderItemId(UUID orderItemId);
}
