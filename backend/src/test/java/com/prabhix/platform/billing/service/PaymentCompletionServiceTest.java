package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingInvoice;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingInvoiceRepository;
import com.prabhix.platform.billing.repository.BillingOrderRepository;
import com.prabhix.platform.billing.repository.BillingPaymentRepository;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.config.PrabhixProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionServiceTest {

    @Mock
    private BillingOrderRepository orderRepository;
    @Mock
    private BillingPaymentRepository paymentRepository;
    @Mock
    private BillingSubscriptionRepository subscriptionRepository;
    @Mock
    private BillingPlanRepository planRepository;
    @Mock
    private BillingInvoiceRepository invoiceRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private BillingOrgReader orgReader;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private PrabhixProperties properties;

    private PaymentCompletionService paymentCompletionService;

    @BeforeEach
    void setUp() {
        paymentCompletionService = new PaymentCompletionService(
                orderRepository,
                paymentRepository,
                subscriptionRepository,
                planRepository,
                invoiceRepository,
                invoiceService,
                entitlementService,
                orgReader,
                events,
                properties);
    }

    @Test
    void duplicateCaptureDoesNotReissueInvoice() {
        UUID orderId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        BillingOrder order = new BillingOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setPlanId(planId);
        order.setPurpose(BillingEnums.OrderPurpose.SUBSCRIPTION_NEW);
        order.setStatus(BillingEnums.OrderStatus.CAPTURED);
        order.setTotalPaise(1000);
        order.setCurrency("INR");
        order.setNotes(Map.of("seats", 5));

        BillingInvoice existing = new BillingInvoice();
        existing.setId(UUID.randomUUID());

        when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
        when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        paymentCompletionService.completeCapture(order, new PaymentCompletionService.PaymentCaptureDetails(
                "pay_1", 1000, "INR", "upi", null, null, null, "user@upi", true));

        verify(invoiceService, never()).issueForOrder(any(), any(), any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void firstCaptureIssuesInvoiceOnce() {
        UUID orderId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        BillingPlan plan = new BillingPlan();
        plan.setId(planId);
        plan.setIncludedSeats(5);
        plan.setAmountPaise(0);
        plan.setPerSeatPaise(0);
        plan.setIntervalType(BillingEnums.PlanInterval.MONTHLY);

        BillingOrder order = new BillingOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setPlanId(planId);
        order.setPurpose(BillingEnums.OrderPurpose.SUBSCRIPTION_NEW);
        order.setStatus(BillingEnums.OrderStatus.CREATED);
        order.setTotalPaise(1000);
        order.setCurrency("INR");
        order.setNotes(Map.of("seats", 5));

        BillingSubscription subscription = new BillingSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setOrganizationId(orgId);

        BillingInvoice invoice = new BillingInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setInvoiceNumber("PBX/2026-27/000001");

        when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.findByOrganizationIdAndStatusIn(any(), any())).thenReturn(Optional.empty());
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            BillingSubscription sub = inv.getArgument(0);
            sub.setId(subscription.getId());
            return sub;
        });
        when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(invoiceService.issueForOrder(any(), any(), any())).thenReturn(invoice);
        when(invoiceService.markPaid(invoice)).thenReturn(invoice);
        when(orgReader.find(orgId)).thenReturn(Optional.empty());

        paymentCompletionService.completeCapture(order, new PaymentCompletionService.PaymentCaptureDetails(
                "pay_1", 1000, "INR", "upi", null, null, null, "user@upi", true));

        verify(invoiceService, times(1)).issueForOrder(any(), any(), any());
        verify(invoiceService, times(1)).markPaid(invoice);
        verify(entitlementService).evictCache(orgId);
    }
}
