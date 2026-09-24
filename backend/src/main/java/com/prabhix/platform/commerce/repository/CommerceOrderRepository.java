package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommerceOrderRepository extends JpaRepository<CommerceOrder, UUID> {

    Optional<CommerceOrder> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CommerceOrder> findByAccessToken(String accessToken);

    Optional<CommerceOrder> findByRazorpayOrderId(String razorpayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CommerceOrder o WHERE o.id = :id")
    Optional<CommerceOrder> lockById(UUID id);

    @Query(value = """
            SELECT o.* FROM commerce_orders o
            WHERE o.organization_id = :orgId
              AND (CAST(:status AS varchar) IS NULL OR o.status = CAST(:status AS varchar))
              AND (CAST(:fromAt AS timestamptz) IS NULL OR o.created_at >= CAST(:fromAt AS timestamptz))
              AND (CAST(:toAt AS timestamptz) IS NULL OR o.created_at <= CAST(:toAt AS timestamptz))
              AND (CAST(:search AS varchar) IS NULL
                   OR o.order_number ILIKE '%' || CAST(:search AS varchar) || '%')
              AND (o.created_at < :cursorAt OR (o.created_at = :cursorAt AND o.id < :cursorId))
            ORDER BY o.created_at DESC, o.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<CommerceOrder> listWithCursor(UUID orgId, String status, Instant fromAt, Instant toAt,
                                       String search, Instant cursorAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT o.* FROM commerce_orders o
            WHERE o.status = 'PENDING_PAYMENT'
              AND o.stock_hold_expires_at IS NOT NULL
              AND o.stock_hold_expires_at < :now
            LIMIT 100
            """, nativeQuery = true)
    List<CommerceOrder> findExpiredStockHolds(Instant now);

    @Query(value = """
            SELECT COALESCE(SUM(total_paise), 0) FROM commerce_orders
            WHERE organization_id = :orgId AND status IN ('PAID', 'FULFILLED')
              AND paid_at >= :since
            """, nativeQuery = true)
    long sumRevenueSince(UUID orgId, Instant since);

    @Query(value = """
            SELECT count(*) FROM commerce_orders
            WHERE organization_id = :orgId AND status IN ('PAID', 'FULFILLED')
              AND paid_at >= :since
            """, nativeQuery = true)
    long countOrdersSince(UUID orgId, Instant since);

    @Query(value = """
            SELECT CAST(paid_at AS date) AS day, count(*) AS cnt
            FROM commerce_orders
            WHERE organization_id = :orgId AND status IN ('PAID', 'FULFILLED')
              AND paid_at >= :since
            GROUP BY CAST(paid_at AS date)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> countOrdersByDay(UUID orgId, Instant since);

    @Query(value = """
            SELECT o.* FROM commerce_orders o
            WHERE o.customer_id = :customerId AND o.organization_id = :orgId
            ORDER BY o.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<CommerceOrder> listByCustomer(UUID orgId, UUID customerId, int limit);
}
