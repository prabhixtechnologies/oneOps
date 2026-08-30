package com.prabhix.platform.billing.service;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingWebhookEvent;
import com.prabhix.platform.billing.repository.BillingOrderRepository;
import com.prabhix.platform.billing.repository.BillingPaymentRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.billing.repository.BillingWebhookEventRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private BillingWebhookEventRepository webhookEventRepository;
    @Mock
    private BillingOrderRepository orderRepository;
    @Mock
    private BillingPaymentRepository paymentRepository;
    @Mock
    private BillingSubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentCompletionService paymentCompletionService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private BillingPaymentInstrumentService paymentInstrumentService;
    @Mock
    private PrabhixProperties properties;

    private WebhookService webhookService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                webhookEventRepository,
                orderRepository,
                paymentRepository,
                subscriptionRepository,
                paymentCompletionService,
                entitlementService,
                paymentInstrumentService,
                objectMapper,
                properties);
    }

    @Test
    void duplicateProviderEventIdIsNoOp() {
        when(properties.billing()).thenReturn(new PrabhixProperties.Billing(
                new PrabhixProperties.Billing.Razorpay("", "", "secret", "https://api.razorpay.com/v1"),
                "INR",
                new PrabhixProperties.Billing.Invoice("PBX", 18),
                14));
        when(webhookEventRepository.findByProviderAndProviderEventId(
                BillingEnums.WebhookProvider.RAZORPAY, "evt_dup"))
                .thenReturn(Optional.of(new BillingWebhookEvent()));

        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String signature = com.prabhix.platform.billing.razorpay.RazorpaySignature
                .hmacSha256Hex(new String(body, StandardCharsets.UTF_8), "secret");

        assertDoesNotThrow(() -> webhookService.receive(signature, "evt_dup", body));
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void unverifiedSignatureIsRejected() {
        when(properties.billing()).thenReturn(new PrabhixProperties.Billing(
                new PrabhixProperties.Billing.Razorpay("", "", "secret", "https://api.razorpay.com/v1"),
                "INR",
                new PrabhixProperties.Billing.Invoice("PBX", 18),
                14));

        ApiException ex = assertThrows(ApiException.class, () ->
                webhookService.receive("bad-signature", "evt_1", "{}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(ErrorCode.PAYMENT_SIGNATURE_MISMATCH, ex.getCode());
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void processingFailureStillPersistsEvent() {
        when(properties.billing()).thenReturn(new PrabhixProperties.Billing(
                new PrabhixProperties.Billing.Razorpay("", "", "secret", "https://api.razorpay.com/v1"),
                "INR",
                new PrabhixProperties.Billing.Invoice("PBX", 18),
                14));
        when(webhookEventRepository.findByProviderAndProviderEventId(any(), any()))
                .thenReturn(Optional.empty());
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        byte[] body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"missing"}}}}
                """.strip().getBytes(StandardCharsets.UTF_8);
        String signature = com.prabhix.platform.billing.razorpay.RazorpaySignature
                .hmacSha256Hex(new String(body, StandardCharsets.UTF_8), "secret");

        assertDoesNotThrow(() -> webhookService.receive(signature, "evt_new", body));

        ArgumentCaptor<BillingWebhookEvent> captor = ArgumentCaptor.forClass(BillingWebhookEvent.class);
        verify(webhookEventRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        BillingWebhookEvent saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(BillingEnums.WebhookEventStatus.PROCESSED, saved.getStatus());
    }
}
