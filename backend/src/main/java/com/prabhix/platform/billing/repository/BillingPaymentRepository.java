package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {

    Optional<BillingPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    List<BillingPayment> findByOrganizationIdAndStatusOrderByCapturedAtDesc(
            UUID organizationId, BillingEnums.PaymentStatus status);
}
