package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationTest {

    @Mock private ProductVariantRepository variantRepository;
    @Mock private CommerceOrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private StockService stockService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stockService = new StockService(variantRepository, orderRepository, orderItemRepository);
    }

    @Test
    void reserveCallsAtomicUpdate() {
        CommerceOrder order = new CommerceOrder();
        order.setId(UUID.randomUUID());
        order.setOrganizationId(orgId);
        OrderItem item = new OrderItem();
        item.setVariantId(variantId);
        item.setQuantity(2);
        when(variantRepository.reserveStock(orgId, variantId, 2)).thenReturn(1);
        stockService.reserveForOrder(order, List.of(item));
        verify(variantRepository).reserveStock(orgId, variantId, 2);
    }

    @Test
    void expiryJobReleasesPendingOrders() {
        CommerceOrder order = new CommerceOrder();
        order.setId(UUID.randomUUID());
        order.setOrganizationId(orgId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setOrderNumber("ORD-1");
        when(orderRepository.findExpiredStockHolds(any(Instant.class))).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderIdAndOrganizationId(order.getId(), orgId)).thenReturn(List.of());
        when(orderRepository.save(order)).thenReturn(order);

        stockService.releaseExpiredHolds();

        verify(orderRepository).save(order);
    }
}
