package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private BillingPlanRepository planRepository;
    @Mock
    private BillingSubscriptionRepository subscriptionRepository;
    @Mock
    private BillingOrderRepository orderRepository;
    @Mock
    private com.prabhix.platform.billing.razorpay.RazorpayClient razorpayClient;
    @Mock
    private PrabhixProperties properties;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private BillingOrgReader orgReader;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private PaymentCompletionService paymentCompletionService;
    @Mock
    private BillingPaymentInstrumentService paymentInstrumentService;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                planRepository,
                subscriptionRepository,
                orderRepository,
                razorpayClient,
                properties,
                entitlementService,
                orgReader,
                events,
                paymentCompletionService,
                paymentInstrumentService);
    }

    @Test
    void createOrderAmountDerivedFromPlanNotClient() {
        BillingPlan plan = new BillingPlan();
        plan.setId(UUID.randomUUID());
        plan.setPlanKey("growth-monthly");
        plan.setName("Growth");
        plan.setActive(true);
        plan.setAmountPaise(249_900);
        plan.setPerSeatPaise(29_900);
        plan.setIncludedSeats(10);
        plan.setMaxSeats(50);
        plan.setIntervalType(BillingEnums.PlanInterval.MONTHLY);

        when(planRepository.findByPlanKey("growth-monthly")).thenReturn(Optional.of(plan));
        when(properties.billing()).thenReturn(new PrabhixProperties.Billing(
                new PrabhixProperties.Billing.Razorpay("", "", "", "https://api.razorpay.com/v1"),
                "INR",
                new PrabhixProperties.Billing.Invoice("PBX", 18),
                14));
        when(orgReader.find(any())).thenReturn(Optional.of(
                new BillingOrgReader.BillingOrgSnapshot("Acme", null, null, null, Map.of("state", "Karnataka"))));
        when(orgReader.placeOfSupply()).thenReturn("Karnataka");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID orgId = UUID.randomUUID();
        var result = billingService.createOrder(orgId, UUID.randomUUID(), new com.prabhix.platform.billing.dto.BillingDtos.CreateOrderRequest("growth-monthly", 15));

        assertEquals(399_400, result.amountPaise());
        assertEquals(true, result.localDevCheckout());
    }
}
