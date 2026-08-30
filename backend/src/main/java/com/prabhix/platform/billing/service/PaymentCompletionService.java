package com.prabhix.platform.billing.service;

import tools.jackson.databind.JsonNode;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingInvoice;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingPayment;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingInvoiceRepository;
import com.prabhix.platform.billing.repository.BillingOrderRepository;
import com.prabhix.platform.billing.repository.BillingPaymentRepository;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.MailRequested;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single entry point for marking an order captured and fulfilling entitlements.
 * Both the checkout verify path and Razorpay webhooks delegate here; order status
 * and the invoice-order unique index make repeated calls safe.
 */
@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private static final List<BillingEnums.SubscriptionStatus> LIVE_STATUSES = List.of(
            BillingEnums.SubscriptionStatus.TRIALING,
            BillingEnums.SubscriptionStatus.ACTIVE,
            BillingEnums.SubscriptionStatus.PAST_DUE,
            BillingEnums.SubscriptionStatus.PAUSED);

    private final BillingOrderRepository orderRepository;
    private final BillingPaymentRepository paymentRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository planRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final EntitlementService entitlementService;
    private final BillingOrgReader orgReader;
    private final ApplicationEventPublisher events;
    private final PrabhixProperties properties;

    public record PaymentCaptureDetails(
            String razorpayPaymentId,
            long amountPaise,
            String currency,
            String method,
            String methodDetail,
            String bank,
            String wallet,
            String vpa,
            boolean signatureVerified) {
    }

    /**
     * Idempotent: already-captured orders short-circuit after ensuring invoice exists.
     */
    @Transactional
    public BillingOrder completeCapture(BillingOrder order, PaymentCaptureDetails capture) {
        order = orderRepository.lockById(order.getId())
                .orElseThrow(() -> ApiException.notFound("Order"));

        if (order.getStatus() == BillingEnums.OrderStatus.CAPTURED) {
            ensureInvoiceIssued(order);
            return order;
        }

        order.setStatus(BillingEnums.OrderStatus.CAPTURED);
        if (capture.razorpayPaymentId() != null) {
            order.setRazorpayPaymentId(capture.razorpayPaymentId());
        }
        order.setCapturedAt(Instant.now());
        orderRepository.save(order);

        upsertPayment(order, capture);
        BillingSubscription subscription = fulfillEntitlements(order);
        BillingInvoice invoice = issueInvoiceIfAbsent(order, subscription);
        orgReader.activateOrganization(order.getOrganizationId());
        entitlementService.evictCache(order.getOrganizationId());
        sendPaymentSuccessMail(order, subscription, invoice);

        return order;
    }

    @Transactional
    public BillingOrder completeCaptureForLocalDev(UUID organizationId, UUID orderId) {
        if (properties.billing().razorpay().configured()) {
            throw ApiException.invalidState(
                    "Dev order completion is only available without Razorpay credentials");
        }
        BillingOrder order = orderRepository.findById(orderId)
                .filter(o -> organizationId.equals(o.getOrganizationId()))
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() == BillingEnums.OrderStatus.CAPTURED) {
            return order;
        }
        return completeCapture(order, new PaymentCaptureDetails(
                "dev_pay_" + orderId,
                order.getTotalPaise(),
                order.getCurrency(),
                "dev",
                null,
                null,
                null,
                null,
                false));
    }

    private void upsertPayment(BillingOrder order, PaymentCaptureDetails capture) {
        if (capture.razorpayPaymentId() == null) {
            return;
        }
        BillingPayment payment = paymentRepository.findByRazorpayPaymentId(capture.razorpayPaymentId())
                .orElseGet(() -> {
                    BillingPayment created = new BillingPayment();
                    created.setOrganizationId(order.getOrganizationId());
                    created.setOrderId(order.getId());
                    created.setRazorpayPaymentId(capture.razorpayPaymentId());
                    created.setAmountPaise(capture.amountPaise() > 0 ? capture.amountPaise() : order.getTotalPaise());
                    created.setCurrency(capture.currency() != null ? capture.currency() : order.getCurrency());
                    return created;
                });
        payment.setStatus(BillingEnums.PaymentStatus.CAPTURED);
        payment.setMethod(capture.method());
        payment.setMethodDetail(capture.methodDetail());
        payment.setBank(capture.bank());
        payment.setWallet(capture.wallet());
        payment.setVpa(capture.vpa());
        payment.setSignatureVerified(capture.signatureVerified());
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);
    }

    private BillingSubscription fulfillEntitlements(BillingOrder order) {
        return switch (order.getPurpose()) {
            case SUBSCRIPTION_NEW -> activateNewSubscription(order);
            case UPGRADE, SEAT_ADDITION -> applyPaidChange(order);
            case SUBSCRIPTION_RENEWAL -> renewSubscription(order);
            default -> subscriptionRepository
                    .findByOrganizationIdAndStatusIn(order.getOrganizationId(), LIVE_STATUSES)
                    .orElse(null);
        };
    }

    private BillingSubscription activateNewSubscription(BillingOrder order) {
        BillingPlan plan = requirePlan(order.getPlanId());
        int seats = seatsFromNotes(order, plan);

        BillingSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatusIn(order.getOrganizationId(), LIVE_STATUSES)
                .orElseGet(() -> {
                    BillingSubscription created = new BillingSubscription();
                    created.setOrganizationId(order.getOrganizationId());
                    created.setCurrentPeriodStart(Instant.now());
                    created.setCurrentPeriodEnd(Instant.now().plus(periodDays(plan), ChronoUnit.DAYS));
                    created.setCancelAtPeriodEnd(false);
                    return created;
                });

        subscription.setPlanId(plan.getId());
        subscription.setSeats(seats);
        subscription.setStatus(BillingEnums.SubscriptionStatus.ACTIVE);
        subscription.setLockedAmountPaise(plan.getAmountPaise());
        subscription.setLockedPerSeatPaise(plan.getPerSeatPaise());
        subscription.setLastPaymentAt(Instant.now());
        subscription.setNextBillingAt(subscription.getCurrentPeriodEnd());
        subscription.setFailedPaymentCount(0);
        subscription.setGracePeriodEndsAt(null);
        subscription.setPendingPlanId(null);
        subscription.setPendingSeats(null);
        subscription.setPendingChangeAt(null);
        subscription = subscriptionRepository.save(subscription);

        order.setSubscriptionId(subscription.getId());
        orderRepository.save(order);
        syncOrganizationSeatLimit(subscription);
        return subscription;
    }

    private BillingSubscription applyPaidChange(BillingOrder order) {
        BillingPlan plan = requirePlan(order.getPlanId());
        int seats = seatsFromNotes(order, plan);
        UUID targetPlanId = planIdFromNotes(order, plan.getId());
        BillingPlan targetPlan = planRepository.findById(targetPlanId).orElse(plan);

        BillingSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatusIn(order.getOrganizationId(), LIVE_STATUSES)
                .orElseThrow(() -> ApiException.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No active subscription for this organization"));

        subscription.setPlanId(targetPlan.getId());
        subscription.setSeats(seats);
        subscription.setLockedAmountPaise(targetPlan.getAmountPaise());
        subscription.setLockedPerSeatPaise(targetPlan.getPerSeatPaise());
        subscription.setLastPaymentAt(Instant.now());
        subscription.setFailedPaymentCount(0);
        subscription.setGracePeriodEndsAt(null);
        subscription.setPendingPlanId(null);
        subscription.setPendingSeats(null);
        subscription.setPendingChangeAt(null);
        subscription = subscriptionRepository.save(subscription);

        order.setSubscriptionId(subscription.getId());
        orderRepository.save(order);
        syncOrganizationSeatLimit(subscription);
        return subscription;
    }

    private BillingSubscription renewSubscription(BillingOrder order) {
        BillingSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatusIn(order.getOrganizationId(), LIVE_STATUSES)
                .orElseThrow(() -> ApiException.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No active subscription for this organization"));

        BillingPlan plan = requirePlan(subscription.getPlanId());
        Instant periodStart = subscription.getCurrentPeriodEnd();
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodStart.plus(periodDays(plan), ChronoUnit.DAYS));
        subscription.setNextBillingAt(subscription.getCurrentPeriodEnd());
        subscription.setLastPaymentAt(Instant.now());
        subscription.setFailedPaymentCount(0);
        subscription.setGracePeriodEndsAt(null);
        subscription.setStatus(BillingEnums.SubscriptionStatus.ACTIVE);
        subscription = subscriptionRepository.save(subscription);

        order.setSubscriptionId(subscription.getId());
        orderRepository.save(order);
        return subscription;
    }

    /** Returns the invoice for this order, issuing it only if one does not already exist. */
    private BillingInvoice issueInvoiceIfAbsent(BillingOrder order, BillingSubscription subscription) {
        var existing = invoiceRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        BillingPlan plan = requirePlan(order.getPlanId());
        BillingInvoice invoice = invoiceService.issueForOrder(order, plan, subscription);
        return invoiceService.markPaid(invoice);
    }

    private void ensureInvoiceIssued(BillingOrder order) {
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            return;
        }
        BillingSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatusIn(order.getOrganizationId(), LIVE_STATUSES)
                .orElse(null);
        issueInvoiceIfAbsent(order, subscription);
    }

    private void sendPaymentSuccessMail(BillingOrder order,
                                        BillingSubscription subscription,
                                        BillingInvoice invoice) {
        if (subscription == null || invoice == null) {
            return;
        }
        BillingPlan plan = requirePlan(order.getPlanId());
        orgReader.find(order.getOrganizationId()).ifPresent(org -> {
            String email = org.billingEmail();
            if (email == null || email.isBlank()) {
                return;
            }
            events.publishEvent(MailRequested.forOrganization(
                    order.getOrganizationId(),
                    email,
                    "billing.payment-succeeded",
                    Map.of(
                            "amount", order.getTotalPaise(),
                            "planName", plan.getName(),
                            "invoiceNumber", invoice.getInvoiceNumber(),
                            "paidOn", Instant.now().toString(),
                            "nextRenewal", subscription.getNextBillingAt() == null
                                    ? "" : subscription.getNextBillingAt().toString(),
                            "invoiceUrl", "/api/v1/billing/invoices/" + invoice.getId() + "/download"),
                    "payment-success-" + order.getId()));
        });
    }

    private void syncOrganizationSeatLimit(BillingSubscription subscription) {
        orgReader.updateSeatLimit(subscription.getOrganizationId(), subscription.getSeats());
    }

    private BillingPlan requirePlan(UUID planId) {
        if (planId == null) {
            throw ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found");
        }
        return planRepository.findById(planId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));
    }

    private int seatsFromNotes(BillingOrder order, BillingPlan plan) {
        if (order.getNotes() != null && order.getNotes().containsKey("seats")) {
            return ((Number) order.getNotes().get("seats")).intValue();
        }
        return plan.getIncludedSeats();
    }

    private UUID planIdFromNotes(BillingOrder order, UUID fallback) {
        if (order.getNotes() != null && order.getNotes().containsKey("targetPlanId")) {
            return UUID.fromString(order.getNotes().get("targetPlanId").toString());
        }
        return fallback;
    }

    private int periodDays(BillingPlan plan) {
        return switch (plan.getIntervalType()) {
            case ANNUAL -> 365;
            case MONTHLY -> 30;
            default -> 30;
        };
    }

    public static PaymentCaptureDetails fromGatewayPayment(JsonNode payment, boolean signatureVerified) {
        String method = payment.path("method").asText(null);
        String methodDetail = null;
        if ("card".equals(method)) {
            methodDetail = payment.path("card").path("last4").asText(null);
        }
        return new PaymentCaptureDetails(
                payment.path("id").asText(null),
                payment.path("amount").asLong(0),
                payment.path("currency").asText("INR"),
                method,
                methodDetail,
                payment.path("bank").asText(null),
                payment.path("wallet").asText(null),
                payment.path("vpa").asText(null),
                signatureVerified);
    }
}
