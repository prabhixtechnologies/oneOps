package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {

    Optional<BillingPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    List<BillingPayment> findByOrganizationIdAndStatusOrderByCapturedAtDesc(
            UUID organizationId, BillingEnums.PaymentStatus status);

    @Query("""
            select p.status, coalesce(sum(p.amountPaise), 0), count(p)
            from BillingPayment p
            group by p.status
            """)
    List<Object[]> aggregateByStatus();

    List<BillingPayment> findTop50ByOrderByCapturedAtDesc();
}
