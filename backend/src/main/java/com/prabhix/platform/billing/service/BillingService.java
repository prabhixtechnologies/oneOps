package com.prabhix.platform.billing.service;

import tools.jackson.databind.JsonNode;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.dto.BillingDtos.BillingChangeResponse;
import com.prabhix.platform.billing.dto.BillingDtos.CancelSubscriptionRequest;
import com.prabhix.platform.billing.dto.BillingDtos.ChangePlanRequest;
import com.prabhix.platform.billing.dto.BillingDtos.ChangeSeatsRequest;
import com.prabhix.platform.billing.dto.BillingDtos.CreateOrderRequest;
import com.prabhix.platform.billing.dto.BillingDtos.OrderView;
import com.prabhix.platform.billing.dto.BillingDtos.PlanView;
import com.prabhix.platform.billing.dto.BillingDtos.SubscriptionView;
import com.prabhix.platform.billing.dto.BillingDtos.VerifyPaymentRequest;
import com.prabhix.platform.billing.dto.BillingDtos.VerifyPaymentResponse;
import com.prabhix.platform.billing.razorpay.RazorpayClient;
import com.prabhix.platform.billing.razorpay.RazorpaySignature;
import com.prabhix.platform.billing.repository.BillingOrderRepository;
import com.prabhix.platform.billing.repository.BillingPlanRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final List<BillingEnums.SubscriptionStatus> LIVE_STATUSES = List.of(
            BillingEnums.SubscriptionStatus.TRIALING,
            BillingEnums.SubscriptionStatus.ACTIVE,
            BillingEnums.SubscriptionStatus.PAST_DUE,
            BillingEnums.SubscriptionStatus.PAUSED);

    private final BillingPlanRepository planRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingOrderRepository orderRepository;
    private final RazorpayClient razorpayClient;
    private final PrabhixProperties properties;
    private final EntitlementService entitlementService;
    private final BillingOrgReader orgReader;
    private final ApplicationEventPublisher events;
    private final PaymentCompletionService paymentCompletionService;
    private final BillingPaymentInstrumentService paymentInstrumentService;
    private final StructuredEventLogger eventLogger;

    @Transactional(readOnly = true)
    public PageResponse<PlanView> listPlans(int page, int size) {
        var plans = planRepository.findByIsPublicTrueAndIsActiveTrueOrderByRankAsc(
                PageRequest.of(page, size));
        return PageResponse.from(plans, this::toPlanView);
    }

    @Transactional(readOnly = true)
    public SubscriptionView getSubscription(UUID organizationId) {
        BillingSubscription subscription = activeSubscription(organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No active subscription for this organization"));
        BillingPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));
        return toSubscriptionView(subscription, plan.getName());
    }

    @Transactional
    public OrderView createOrder(UUID organizationId, UUID userId, CreateOrderRequest request) {
        BillingPlan plan = planRepository.findByPlanKey(request.planKey())
                .filter(BillingPlan::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));

        int seats = request.seats();
        entitlementService.requirePlanSeats(plan, seats);
        long amountPaise = BillingAmountCalculator.computePlanAmountPaise(plan, seats);
        var tax = BillingAmountCalculator.computeTax(
                amountPaise,
                properties.billing().invoice().gstPercent(),
                orgBuyerState(organizationId),
                placeOfSupply());

        BillingOrder order = new BillingOrder();
        order.setOrganizationId(organizationId);
        order.setPlanId(plan.getId());
        order.setPurpose(BillingEnums.OrderPurpose.SUBSCRIPTION_NEW);
        order.setAmountPaise(amountPaise);
        order.setTaxPaise(tax.totalTaxPaise());
        order.setTotalPaise(amountPaise + tax.totalTaxPaise());
        order.setCurrency(properties.billing().currency());
        order.setStatus(BillingEnums.OrderStatus.CREATED);
        order.setReceipt("rcpt_" + Ids.token(12));
        order.setInitiatedBy(userId);
        order.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        order.setNotes(Map.of("planKey", plan.getPlanKey(), "seats", seats));
        order = orderRepository.save(order);

        createGatewayOrder(order, organizationId);

        events.publishEvent(AuditRequested.of(
                organizationId, userId, "billing.order.created", "billing_order", order.getId()));
        eventLogger.log(LogEventCode.BILLING_PAYMENT_INITIATED, Map.of(
                "orderId", order.getId(), "organizationId", organizationId));

        return toOrderView(order);
    }

    @Transactional
    public VerifyPaymentResponse verifyCheckout(UUID organizationId, VerifyPaymentRequest request) {
        BillingOrder order = orderRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .filter(o -> organizationId.equals(o.getOrganizationId()))
                .orElseThrow(() -> ApiException.notFound("Order"));

        if (order.getStatus() == BillingEnums.OrderStatus.CAPTURED) {
            return new VerifyPaymentResponse(order.getId(), order.getStatus(), true);
        }

        String secret = properties.billing().razorpay().keySecret();
        boolean verified = RazorpaySignature.verifyCheckout(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature(),
                secret);
        if (!verified) {
            throw ApiException.of(ErrorCode.PAYMENT_SIGNATURE_MISMATCH, "Payment signature did not match");
        }

        PaymentCompletionService.PaymentCaptureDetails capture =
                new PaymentCompletionService.PaymentCaptureDetails(
                        request.razorpayPaymentId(),
                        order.getTotalPaise(),
                        order.getCurrency(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        true);

        JsonNode gatewayPayment = null;
        if (properties.billing().razorpay().configured()) {
            gatewayPayment = razorpayClient.fetchPayment(request.razorpayPaymentId());
            if (!"captured".equals(gatewayPayment.path("status").asText())) {
                throw ApiException.invalidState("Payment has not been captured at the gateway yet");
            }
            capture = PaymentCompletionService.fromGatewayPayment(gatewayPayment, true);
        }

        order = paymentCompletionService.completeCapture(order, capture);
        if (gatewayPayment != null) {
            paymentInstrumentService.captureFromGatewayPayment(organizationId, gatewayPayment);
        }
        return new VerifyPaymentResponse(order.getId(), order.getStatus(), true);
    }

    @Transactional
    public OrderView completeDevOrder(UUID organizationId, UUID orderId) {
        BillingOrder order = paymentCompletionService.completeCaptureForLocalDev(organizationId, orderId);
        return toOrderView(order);
    }

    @Transactional
    public BillingChangeResponse changePlan(UUID organizationId, UUID userId, ChangePlanRequest request) {
        BillingSubscription subscription = requireLiveSubscription(organizationId);
        BillingPlan currentPlan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));
        BillingPlan newPlan = planRepository.findByPlanKey(request.planKey())
                .filter(BillingPlan::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));

        int seats = request.seats();
        entitlementService.requirePlanSeats(newPlan, seats);

        long newAmount = BillingAmountCalculator.computePlanAmountPaise(newPlan, seats);
        long proration = computeProration(subscription, newAmount);

        clearPendingDowngrade(subscription);

        if (proration > 0) {
            BillingOrder order = createProrationOrder(
                    organizationId, userId, subscription, newPlan, seats, proration,
                    BillingEnums.OrderPurpose.UPGRADE);
            events.publishEvent(AuditRequested.of(
                    organizationId, userId, "billing.plan.upgrade.checkout", "billing_order", order.getId()));
            return new BillingChangeResponse(
                    toSubscriptionView(subscription, currentPlan.getName()),
                    toOrderView(order),
                    "Payment required before the plan upgrade takes effect");
        }

        if (proration < 0 || isDowngrade(currentPlan, newPlan, seats, subscription)) {
            schedulePendingChange(subscription, newPlan.getId(), seats);
            events.publishEvent(AuditRequested.changed(
                    organizationId, userId, "billing.plan.downgrade.scheduled", "billing_subscription",
                    subscription.getId(), Map.of("planKey", newPlan.getPlanKey(), "seats", seats,
                            "effectiveAt", subscription.getPendingChangeAt())));
            return new BillingChangeResponse(
                    toSubscriptionView(subscription, currentPlan.getName()),
                    null,
                    "Downgrade scheduled for end of current billing period");
        }

        subscription.setPlanId(newPlan.getId());
        subscription.setSeats(seats);
        subscription.setLockedAmountPaise(newPlan.getAmountPaise());
        subscription.setLockedPerSeatPaise(newPlan.getPerSeatPaise());
        subscriptionRepository.save(subscription);
        orgReader.updateSeatLimit(organizationId, seats);
        entitlementService.evictCache(organizationId);

        events.publishEvent(AuditRequested.changed(
                organizationId, userId, "billing.plan.changed", "billing_subscription",
                subscription.getId(), Map.of("planKey", newPlan.getPlanKey(), "seats", seats)));
        eventLogger.log(LogEventCode.BILLING_SUBSCRIPTION_CHANGED, Map.of(
                "subscriptionId", subscription.getId(), "organizationId", organizationId));

        return new BillingChangeResponse(
                toSubscriptionView(subscription, newPlan.getName()),
                null,
                "Plan changed immediately");
    }

    @Transactional
    public BillingChangeResponse changeSeats(UUID organizationId, UUID userId, ChangeSeatsRequest request) {
        BillingSubscription subscription = requireLiveSubscription(organizationId);
        BillingPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));

        int targetSeats = request.seats();
        if (targetSeats == subscription.getSeats()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Seat count is unchanged");
        }
        entitlementService.requirePlanSeats(plan, targetSeats);

        if (targetSeats > subscription.getSeats()) {
            long oldAmount = BillingAmountCalculator.computePlanAmountPaise(plan, subscription.getSeats());
            long newAmount = BillingAmountCalculator.computePlanAmountPaise(plan, targetSeats);
            long proration = computeProration(subscription, newAmount);
            if (proration <= 0) {
                proration = Math.max(0, newAmount - oldAmount);
            }

            clearPendingDowngrade(subscription);
            BillingOrder order = createProrationOrder(
                    organizationId, userId, subscription, plan, targetSeats, proration,
                    BillingEnums.OrderPurpose.SEAT_ADDITION);
            events.publishEvent(AuditRequested.of(
                    organizationId, userId, "billing.seats.upgrade.checkout", "billing_order", order.getId()));
            return new BillingChangeResponse(
                    toSubscriptionView(subscription, plan.getName()),
                    toOrderView(order),
                    "Payment required before additional seats are granted");
        }

        schedulePendingChange(subscription, null, targetSeats);
        events.publishEvent(AuditRequested.changed(
                organizationId, userId, "billing.seats.reduction.scheduled", "billing_subscription",
                subscription.getId(), Map.of("seats", targetSeats,
                        "effectiveAt", subscription.getPendingChangeAt())));

        return new BillingChangeResponse(
                toSubscriptionView(subscription, plan.getName()),
                null,
                "Seat reduction scheduled for end of current billing period");
    }

    @Transactional
    public SubscriptionView cancel(UUID organizationId, UUID userId, CancelSubscriptionRequest request) {
        BillingSubscription subscription = requireLiveSubscription(organizationId);

        if (request.atPeriodEnd()) {
            subscription.setCancelAtPeriodEnd(true);
            subscription.setCancellationReason(request.reason());
        } else {
            subscription.setStatus(BillingEnums.SubscriptionStatus.CANCELLED);
            subscription.setCancelledAt(Instant.now());
            subscription.setCancelAtPeriodEnd(false);
            subscription.setCancellationReason(request.reason());
            if (subscription.getRazorpaySubscriptionId() != null
                    && properties.billing().razorpay().configured()) {
                razorpayClient.cancelSubscription(subscription.getRazorpaySubscriptionId(), false);
            }
        }
        subscriptionRepository.save(subscription);
        entitlementService.evictCache(organizationId);

        BillingPlan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        events.publishEvent(AuditRequested.of(
                organizationId, userId, "billing.subscription.cancelled",
                "billing_subscription", subscription.getId()));
        eventLogger.log(LogEventCode.BILLING_SUBSCRIPTION_CANCELLED, Map.of(
                "subscriptionId", subscription.getId(), "organizationId", organizationId));

        return toSubscriptionView(subscription, plan == null ? "" : plan.getName());
    }

    @Transactional
    public SubscriptionView reactivate(UUID organizationId, UUID userId) {
        BillingSubscription subscription = subscriptionRepository
                .findByOrganizationIdAndStatusIn(organizationId,
                        List.of(BillingEnums.SubscriptionStatus.CANCELLED,
                                BillingEnums.SubscriptionStatus.PAUSED))
                .orElseThrow(() -> ApiException.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No cancelled subscription to reactivate"));

        subscription.setStatus(BillingEnums.SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(null);
        subscription.setCancellationReason(null);
        subscription.setGracePeriodEndsAt(null);
        subscription.setFailedPaymentCount(0);
        subscriptionRepository.save(subscription);
        entitlementService.evictCache(organizationId);

        BillingPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> ApiException.of(ErrorCode.PLAN_NOT_FOUND, "Plan was not found"));

        events.publishEvent(AuditRequested.of(
                organizationId, userId, "billing.subscription.reactivated",
                "billing_subscription", subscription.getId()));

        return toSubscriptionView(subscription, plan.getName());
    }

    private BillingOrder createProrationOrder(UUID organizationId,
                                              UUID userId,
                                              BillingSubscription subscription,
                                              BillingPlan plan,
                                              int targetSeats,
                                              long amountPaise,
                                              BillingEnums.OrderPurpose purpose) {
        var tax = BillingAmountCalculator.computeTax(
                amountPaise, properties.billing().invoice().gstPercent(),
                orgBuyerState(organizationId), placeOfSupply());

        Map<String, Object> notes = new HashMap<>();
        notes.put("seats", targetSeats);
        notes.put("targetPlanId", plan.getId().toString());
        notes.put("planKey", plan.getPlanKey());
        notes.put("subscriptionId", subscription.getId().toString());

        BillingOrder order = new BillingOrder();
        order.setOrganizationId(organizationId);
        order.setSubscriptionId(subscription.getId());
        order.setPlanId(plan.getId());
        order.setPurpose(purpose);
        order.setAmountPaise(amountPaise);
        order.setTaxPaise(tax.totalTaxPaise());
        order.setTotalPaise(amountPaise + tax.totalTaxPaise());
        order.setCurrency(properties.billing().currency());
        order.setStatus(BillingEnums.OrderStatus.CREATED);
        order.setReceipt("rcpt_" + Ids.token(12));
        order.setInitiatedBy(userId);
        order.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        order.setNotes(notes);
        order = orderRepository.save(order);

        createGatewayOrder(order, organizationId);
        return order;
    }

    private void createGatewayOrder(BillingOrder order, UUID organizationId) {
        if (!properties.billing().razorpay().configured()) {
            return;
        }
        JsonNode gatewayOrder = razorpayClient.createOrder(
                order.getTotalPaise(),
                order.getCurrency(),
                order.getReceipt(),
                Map.of("orderId", order.getId().toString(), "orgId", organizationId.toString()));
        order.setRazorpayOrderId(gatewayOrder.path("id").asText());
        orderRepository.save(order);
    }

    private void schedulePendingChange(BillingSubscription subscription, UUID pendingPlanId, int pendingSeats) {
        subscription.setPendingPlanId(pendingPlanId);
        subscription.setPendingSeats(pendingSeats);
        subscription.setPendingChangeAt(subscription.getCurrentPeriodEnd());
        subscriptionRepository.save(subscription);
    }

    private void clearPendingDowngrade(BillingSubscription subscription) {
        subscription.setPendingPlanId(null);
        subscription.setPendingSeats(null);
        subscription.setPendingChangeAt(null);
        subscriptionRepository.save(subscription);
    }

    private boolean isDowngrade(BillingPlan current, BillingPlan target, int seats, BillingSubscription sub) {
        long currentAmount = BillingAmountCalculator.computePlanAmountPaise(current, sub.getSeats());
        long targetAmount = BillingAmountCalculator.computePlanAmountPaise(target, seats);
        return targetAmount < currentAmount;
    }

    private long computeProration(BillingSubscription subscription, long newLockedAmount) {
        Instant now = Instant.now();
        if (now.isAfter(subscription.getCurrentPeriodEnd())) {
            return newLockedAmount;
        }
        long periodMillis = subscription.getCurrentPeriodEnd().toEpochMilli()
                - subscription.getCurrentPeriodStart().toEpochMilli();
        long remainingMillis = subscription.getCurrentPeriodEnd().toEpochMilli() - now.toEpochMilli();
        if (periodMillis <= 0) {
            return newLockedAmount;
        }
        BillingPlan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        long oldLocked = plan == null
                ? subscription.getLockedAmountPaise()
                : BillingAmountCalculator.computePlanAmountPaise(plan, subscription.getSeats());
        double fraction = (double) remainingMillis / periodMillis;
        return Math.round((newLockedAmount - oldLocked) * fraction);
    }

    private BillingSubscription requireLiveSubscription(UUID organizationId) {
        return activeSubscription(organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No active subscription for this organization"));
    }

    private java.util.Optional<BillingSubscription> activeSubscription(UUID organizationId) {
        return subscriptionRepository.findByOrganizationIdAndStatusIn(organizationId, LIVE_STATUSES);
    }

    private String orgBuyerState(UUID organizationId) {
        return orgReader.find(organizationId).map(BillingOrgReader.BillingOrgSnapshot::buyerState).orElse(null);
    }

    private String placeOfSupply() {
        return orgReader.placeOfSupply();
    }

    private PlanView toPlanView(BillingPlan plan) {
        return new PlanView(
                plan.getId(), plan.getPlanKey(), plan.getName(), plan.getDescription(),
                plan.getIntervalType(), plan.getAmountPaise(), plan.getPerSeatPaise(),
                plan.getIncludedSeats(), plan.getMaxSeats(), plan.getTrialDays(),
                plan.getEntitlements());
    }

    private SubscriptionView toSubscriptionView(BillingSubscription subscription, String planName) {
        return new SubscriptionView(
                subscription.getId(), subscription.getPlanId(), planName, subscription.getStatus(),
                subscription.getSeats(), subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(), subscription.getTrialEndsAt(),
                subscription.isCancelAtPeriodEnd(), subscription.getNextBillingAt(),
                subscription.getLockedAmountPaise(), subscription.getLockedPerSeatPaise(),
                subscription.getPendingPlanId(), subscription.getPendingSeats(),
                subscription.getPendingChangeAt());
    }

    private OrderView toOrderView(BillingOrder order) {
        return new OrderView(
                order.getId(), order.getRazorpayOrderId(), order.getAmountPaise(),
                order.getTaxPaise(), order.getTotalPaise(), order.getCurrency(),
                order.getStatus(), order.getReceipt(),
                !properties.billing().razorpay().configured());
    }
}
