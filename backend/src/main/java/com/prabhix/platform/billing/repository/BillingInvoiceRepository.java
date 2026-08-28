package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingInvoice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {

    Optional<BillingInvoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<BillingInvoice> findByOrderId(UUID orderId);

    @Query("""
            SELECT i FROM BillingInvoice i
            WHERE i.organizationId = :orgId
              AND (CAST(:cursorCreated AS timestamp) IS NULL
                   OR i.createdAt < :cursorCreated
                   OR (i.createdAt = :cursorCreated AND i.id < :cursorId))
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<BillingInvoice> findPage(@Param("orgId") UUID organizationId,
                                  @Param("cursorCreated") Instant cursorCreated,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);
}
