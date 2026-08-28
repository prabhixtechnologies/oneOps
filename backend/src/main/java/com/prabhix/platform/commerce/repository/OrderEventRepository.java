package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    List<OrderEvent> findByOrderIdAndOrganizationIdOrderByCreatedAtAsc(UUID orderId, UUID organizationId);
}
