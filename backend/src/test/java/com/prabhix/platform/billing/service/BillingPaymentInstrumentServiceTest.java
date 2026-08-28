package com.prabhix.platform.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingPaymentInstrumentServiceTest {

    @Mock private BillingSubscriptionRepository subscriptionRepository;

    private BillingPaymentInstrumentService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new BillingPaymentInstrumentService(subscriptionRepository);
    }

    @Test
    void capturesTokenFromGatewayPayment() throws Exception {
        UUID orgId = UUID.randomUUID();
        BillingSubscription subscription = new BillingSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setOrganizationId(orgId);

        when(subscriptionRepository.findByOrganizationIdAndStatusIn(any(), any()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var payment = objectMapper.readTree("""
                {"token_id":"tok_abc","customer_id":"cust_xyz"}
                """);

        service.captureFromGatewayPayment(orgId, payment);

        verify(subscriptionRepository).save(subscription);
        org.junit.jupiter.api.Assertions.assertEquals("tok_abc", subscription.getRazorpayTokenId());
        org.junit.jupiter.api.Assertions.assertEquals("cust_xyz", subscription.getRazorpayCustomerId());
    }
}
