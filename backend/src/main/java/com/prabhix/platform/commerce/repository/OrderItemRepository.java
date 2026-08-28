package com.prabhix.platform.commerce.repository;

import com.prabhix.platform.commerce.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderIdAndOrganizationId(UUID orderId, UUID organizationId);

    @Query(value = """
            SELECT oi.product_id, SUM(oi.quantity) AS qty
            FROM commerce_order_items oi
            JOIN commerce_orders o ON o.id = oi.order_id
            WHERE o.organization_id = :orgId AND o.status IN ('PAID', 'FULFILLED')
              AND o.paid_at >= :since
            GROUP BY oi.product_id
            ORDER BY qty DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topProductsSince(UUID orgId, Instant since, int limit);
}
