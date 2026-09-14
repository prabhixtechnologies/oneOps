package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.OrderShipment;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.domain.CommerceEnums.ShipmentStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderAddressRepository;
import com.prabhix.platform.commerce.repository.OrderDownloadRepository;
import com.prabhix.platform.commerce.repository.OrderEventRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.commerce.repository.OrderShipmentRepository;
import com.prabhix.platform.commerce.repository.ProductRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceOrderServiceFulfillTest {

    @Mock private CommerceOrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderAddressRepository addressRepository;
    @Mock private OrderEventRepository eventRepository;
    @Mock private CommerceCustomerRepository customerRepository;
    @Mock private OrderDownloadRepository downloadRepository;
    @Mock private OrderShipmentRepository shipmentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockService stockService;
    @Mock private ApplicationEventPublisher events;
    @Mock private StructuredEventLogger eventLogger;
    @Mock private PrabhixPrincipal principal;

    private CommerceOrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new CommerceOrderService(
                orderRepository, orderItemRepository, addressRepository, eventRepository,
                customerRepository, downloadRepository, shipmentRepository, productRepository,
                stockService, events, eventLogger);
    }

    @Test
    void fulfillRecordsCarrierAndTrackingOnShipment() {
        UUID orgId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(principal.requireOrganizationId()).thenReturn(orgId);
        when(principal.userId()).thenReturn(UUID.randomUUID());

        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setStatus(OrderStatus.PAID);
        order.setOrderNumber("ORD-1");

        OrderShipment shipment = new OrderShipment();
        shipment.setOrderItemId(UUID.randomUUID());
        shipment.setStatus(ShipmentStatus.PENDING_PICK);

        when(orderRepository.findByIdAndOrganizationId(orderId, orgId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shipmentRepository.findByOrderIdAndOrganizationId(orderId, orgId)).thenReturn(List.of(shipment));
        when(orderItemRepository.findByOrderIdAndOrganizationId(orderId, orgId)).thenReturn(List.of());
        when(addressRepository.findByOrderIdAndOrganizationId(orderId, orgId)).thenReturn(List.of());
        when(eventRepository.findByOrderIdAndOrganizationIdOrderByCreatedAtAsc(orderId, orgId))
                .thenReturn(List.of());

        orderService.fulfill(principal, orderId, new CommerceDtos.FulfillOrderRequest("BlueDart", "BD123"));

        assertEquals(ShipmentStatus.SHIPPED, shipment.getStatus());
        assertEquals("BlueDart", shipment.getCarrier());
        assertEquals("BD123", shipment.getTrackingNumber());
        verify(shipmentRepository).save(shipment);
    }
}
