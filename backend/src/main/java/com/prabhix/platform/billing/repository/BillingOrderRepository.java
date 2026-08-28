package com.prabhix.platform.billing.repository;

import com.prabhix.platform.billing.domain.BillingOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingOrderRepository extends JpaRepository<BillingOrder, UUID> {

    Optional<BillingOrder> findByRazorpayOrderId(String razorpayOrderId);

    Optional<BillingOrder> findByReceipt(String receipt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM BillingOrder o WHERE o.id = :id")
    Optional<BillingOrder> lockById(@Param("id") UUID id);

    @Query("""
            SELECT o FROM BillingOrder o
            WHERE o.subscriptionId = :subscriptionId
              AND o.purpose = :purpose
              AND o.status IN :statuses
              AND o.createdAt >= :since
            """)
    List<BillingOrder> findRecentBySubscriptionAndPurpose(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("purpose") com.prabhix.platform.billing.domain.BillingEnums.OrderPurpose purpose,
            @Param("statuses") List<com.prabhix.platform.billing.domain.BillingEnums.OrderStatus> statuses,
            @Param("since") Instant since);
}
