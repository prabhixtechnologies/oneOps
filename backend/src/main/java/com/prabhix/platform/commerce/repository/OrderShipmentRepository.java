package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.OrderShipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, UUID> {

    Optional<OrderShipment> findByOrderItemId(UUID orderItemId);

    List<OrderShipment> findByOrderIdAndOrganizationId(UUID orderId, UUID organizationId);
}
