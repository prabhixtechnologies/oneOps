package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.CommercePaymentRepository;
import com.prabhix.platform.commerce.repository.CommerceSubscriptionRepository;
import com.prabhix.platform.commerce.repository.OrderEventRepository;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceWebhookIdempotencyTest {

    @Mock private CommerceOrderRepository orderRepository;
    @Mock private CommercePaymentRepository paymentRepository;
    @Mock private OrderEventRepository eventRepository;
    @Mock private CommerceCustomerRepository customerRepository;
    @Mock private CommerceSubscriptionRepository subscriptionRepository;
    @Mock private StockService stockService;
    @Mock private CommerceInvoiceService invoiceService;
    @Mock private CommerceOrderProvisioningService provisioningService;
    @Mock private CommerceSubscriptionRenewalService subscriptionRenewalService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private ApplicationEventPublisher events;

    private CommercePaymentCompletionService completionService;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        completionService = new CommercePaymentCompletionService(
                orderRepository,
                paymentRepository,
                eventRepository,
                customerRepository,
                subscriptionRepository,
                stockService,
                invoiceService,
                provisioningService,
                subscriptionRenewalService,
                organizationRepository,
                events,
                properties);
    }

    @Test
    void secondCaptureIsNoOp() {
        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(UUID.randomUUID());
        order.setStatus(OrderStatus.PAID);
        order.setTotalMinor(1000);
        order.setCurrency("INR");

        when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
        when(invoiceService.issueForOrder(order)).thenReturn(new com.prabhix.platform.commerce.domain.CommerceInvoice());

        var details = new CommercePaymentCompletionService.CaptureDetails(
                "pay_123", 1000, "INR", "card", true);

        CommerceOrder result = completionService.completeCapture(order, details);

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(stockService, times(0)).commitForOrder(any());
    }
}
