package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderAddressRepository extends JpaRepository<OrderAddress, UUID> {

    List<OrderAddress> findByOrderIdAndOrganizationId(UUID orderId, UUID organizationId);
}
