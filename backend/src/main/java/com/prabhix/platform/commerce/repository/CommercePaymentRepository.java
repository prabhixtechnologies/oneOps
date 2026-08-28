package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommercePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommercePaymentRepository extends JpaRepository<CommercePayment, UUID> {

    Optional<CommercePayment> findByOrderIdAndOrganizationId(UUID orderId, UUID organizationId);

    Optional<CommercePayment> findByRazorpayPaymentId(String razorpayPaymentId);
}
