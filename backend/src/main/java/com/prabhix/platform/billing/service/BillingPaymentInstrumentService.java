package com.prabhix.platform.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Persists reusable Razorpay tokens from successful checkouts for off-session renewals. */
@Service
@RequiredArgsConstructor
public class BillingPaymentInstrumentService {

    private static final List<BillingEnums.SubscriptionStatus> LIVE = List.of(
            BillingEnums.SubscriptionStatus.TRIALING,
            BillingEnums.SubscriptionStatus.ACTIVE,
            BillingEnums.SubscriptionStatus.PAST_DUE,
            BillingEnums.SubscriptionStatus.PAUSED);

    private final BillingSubscriptionRepository subscriptionRepository;

    @Transactional
    public void captureFromGatewayPayment(UUID organizationId, JsonNode payment) {
        String tokenId = payment.path("token_id").asText(null);
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        subscriptionRepository.findByOrganizationIdAndStatusIn(organizationId, LIVE)
                .ifPresent(subscription -> applyToken(subscription, payment));
    }

    @Transactional
    public void captureForSubscription(BillingSubscription subscription, JsonNode payment) {
        applyToken(subscription, payment);
    }

    private void applyToken(BillingSubscription subscription, JsonNode payment) {
        String tokenId = payment.path("token_id").asText(null);
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        subscription.setRazorpayTokenId(tokenId);
        String customerId = payment.path("customer_id").asText(null);
        if (customerId != null && !customerId.isBlank()) {
            subscription.setRazorpayCustomerId(customerId);
        }
        subscriptionRepository.save(subscription);
    }
}
