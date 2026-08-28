package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommerceInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommerceInvoiceRepository extends JpaRepository<CommerceInvoice, UUID> {

    Optional<CommerceInvoice> findByOrderId(UUID orderId);

    Optional<CommerceInvoice> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
