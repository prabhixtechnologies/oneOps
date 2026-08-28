package com.prabhix.platform.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.razorpay.RazorpayClient;
import com.prabhix.platform.billing.repository.BillingOrderRepository;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Self-managed SaaS renewals: we create {@code SUBSCRIPTION_RENEWAL} orders on schedule and
 * attempt off-session charges with a saved token. Razorpay-native subscriptions are not used
 * because initial checkout, proration upgrades, and {@link PaymentCompletionService} already
 * centre on one-time orders — wiring subscriptions would fork that path for little gain.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRenewalService {

    private static final List<BillingEnums.OrderStatus> OPEN_RENEWAL_STATUSES = List.of(
            BillingEnums.OrderStatus.CREATED,
            BillingEnums.OrderStatus.ATTEMPTED);

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingOrderRepository orderRepository;
    private final BillingPlanRepository planRepository;
    private final RazorpayClient razorpayClient;
    private final PaymentCompletionService paymentCompletionService;
    private final BillingOrgReader orgReader;
    private final PrabhixProperties properties;

    public record ChargeAttemptResult(boolean charged, BillingOrder order, String failureReason) {
        public static ChargeAttemptResult skipped() {
            return new ChargeAttemptResult(false, null, "skipped");
        }
    }

    @Transactional
    public BillingOrder createRenewalOrder(BillingSubscription subscription) {
        subscription = subscriptionRepository.lockById(subscription.getId()).orElse(subscription);

        Instant periodStart = subscription.getCurrentPeriodEnd();
        var existing = orderRepository.findRecentBySubscriptionAndPurpose(
                subscription.getId(),
                BillingEnums.OrderPurpose.SUBSCRIPTION_RENEWAL,
                OPEN_RENEWAL_STATUSES,
                periodStart.minus(1, ChronoUnit.HOURS));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        BillingPlan plan = planRepository.findById(subscription.getPlanId()).orElseThrow();
        long amountPaise = subscription.getLockedAmountPaise()
                + Math.max(0, subscription.getSeats()) * subscription.getLockedPerSeatPaise();
        var tax = BillingAmountCalculator.computeTax(
                amountPaise,
                properties.billing().invoice().gstPercent(),
                orgBuyerState(subscription.getOrganizationId()),
                orgReader.placeOfSupply());

        BillingOrder order = new BillingOrder();
        order.setOrganizationId(subscription.getOrganizationId());
        order.setSubscriptionId(subscription.getId());
        order.setPlanId(plan.getId());
        order.setPurpose(BillingEnums.OrderPurpose.SUBSCRIPTION_RENEWAL);
        order.setAmountPaise(amountPaise);
        order.setTaxPaise(tax.totalTaxPaise());
        order.setTotalPaise(amountPaise + tax.totalTaxPaise());
        order.setCurrency(subscription.getCurrency());
        order.setStatus(BillingEnums.OrderStatus.CREATED);
        order.setReceipt("rnwl_" + Ids.token(12));
        order.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        order.setNotes(Map.of(
                "seats", subscription.getSeats(),
                "periodEnd", subscription.getCurrentPeriodEnd().toString()));
        order = orderRepository.save(order);

        if (properties.billing().razorpay().configured()) {
            JsonNode gatewayOrder = razorpayClient.createOrder(
                    order.getTotalPaise(),
                    order.getCurrency(),
                    order.getReceipt(),
                    Map.of(
                            "orderId", order.getId().toString(),
                            "orgId", subscription.getOrganizationId().toString(),
                            "purpose", "SUBSCRIPTION_RENEWAL"));
            order.setRazorpayOrderId(gatewayOrder.path("id").asText());
            orderRepository.save(order);
        }
        return order;
    }

    /**
     * Attempts an off-session charge when a saved token exists. Gateway misconfiguration or missing
     * token returns {@code charged=false} without throwing — callers decide dunning escalation.
     */
    @Transactional
    public ChargeAttemptResult attemptOffSessionCharge(BillingSubscription subscription, BillingOrder order) {
        if (!properties.billing().razorpay().configured()) {
            return ChargeAttemptResult.skipped();
        }
        if (subscription.getRazorpayTokenId() == null || subscription.getRazorpayTokenId().isBlank()) {
            return new ChargeAttemptResult(false, order, "no_saved_token");
        }
        if (order.getRazorpayOrderId() == null) {
            return new ChargeAttemptResult(false, order, "no_gateway_order");
        }

        var org = orgReader.find(subscription.getOrganizationId()).orElse(null);
        if (org == null || org.billingEmail() == null || org.billingEmail().isBlank()) {
            return new ChargeAttemptResult(false, order, "no_billing_email");
        }

        String customerId = subscription.getRazorpayCustomerId();
        if (customerId == null || customerId.isBlank()) {
            JsonNode customer = razorpayClient.createCustomer(
                    org.billingEmail(), null, org.displayName());
            customerId = customer.path("id").asText();
            subscription.setRazorpayCustomerId(customerId);
            subscriptionRepository.save(subscription);
        }

        try {
            JsonNode payment = razorpayClient.createRecurringPayment(
                    org.billingEmail(),
                    null,
                    order.getTotalPaise(),
                    order.getCurrency(),
                    order.getRazorpayOrderId(),
                    customerId,
                    subscription.getRazorpayTokenId());
            String status = payment.path("status").asText();
            if ("captured".equals(status) || "authorized".equals(status)) {
                paymentCompletionService.completeCapture(
                        order, PaymentCompletionService.fromGatewayPayment(payment, false));
                return new ChargeAttemptResult(true, order, null);
            }
            order.setStatus(BillingEnums.OrderStatus.FAILED);
            order.setFailureReason(payment.path("error_description").asText("Charge declined"));
            orderRepository.save(order);
            return new ChargeAttemptResult(false, order, order.getFailureReason());
        } catch (Exception ex) {
            log.warn("Off-session charge failed for subscription {}: {}",
                    subscription.getId(), ex.getMessage());
            order.setStatus(BillingEnums.OrderStatus.FAILED);
            order.setFailureReason(ex.getMessage());
            orderRepository.save(order);
            return new ChargeAttemptResult(false, order, ex.getMessage());
        }
    }

    private String orgBuyerState(UUID organizationId) {
        return orgReader.find(organizationId).map(BillingOrgReader.BillingOrgSnapshot::buyerState).orElse(null);
    }
}
