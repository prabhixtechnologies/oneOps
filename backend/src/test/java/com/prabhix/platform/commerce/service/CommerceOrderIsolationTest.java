package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceOrderIsolationTest {

    @Mock private CommerceOrderRepository orderRepository;
    @Mock private com.prabhix.platform.commerce.repository.OrderItemRepository orderItemRepository;
    @Mock private com.prabhix.platform.commerce.repository.OrderAddressRepository addressRepository;
    @Mock private com.prabhix.platform.commerce.repository.OrderEventRepository eventRepository;
    @Mock private com.prabhix.platform.commerce.repository.CommerceCustomerRepository customerRepository;
    @Mock private com.prabhix.platform.commerce.repository.OrderDownloadRepository downloadRepository;
    @Mock private com.prabhix.platform.commerce.repository.OrderShipmentRepository shipmentRepository;
    @Mock private com.prabhix.platform.commerce.repository.ProductRepository productRepository;
    @Mock private StockService stockService;
    @Mock private ApplicationEventPublisher events;

    private CommerceOrderService orderService;

    private final UUID orgA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = new CommerceOrderService(
                orderRepository, orderItemRepository, addressRepository, eventRepository,
                customerRepository, downloadRepository, shipmentRepository, productRepository,
                stockService, events);
    }

    @Test
    void agentCannotReadForeignTenantOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndOrganizationId(orderId, orgA)).thenReturn(Optional.empty());

        PrabhixPrincipal principal = new PrabhixPrincipal(
                UUID.randomUUID(), "agent@example.com", "Agent",
                orgA, Set.of(Permission.COMMERCE_ORDER_READ), UUID.randomUUID(), false);

        assertThrows(ApiException.class, () -> orderService.get(orgA, orderId));
    }
}
