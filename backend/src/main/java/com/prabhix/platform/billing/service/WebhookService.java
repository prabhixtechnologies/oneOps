package com.prabhix.platform.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final BillingWebhookEventRepository webhookEventRepository;
    private final BillingOrderRepository orderRepository;
    private final BillingPaymentRepository paymentRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final PaymentCompletionService paymentCompletionService;
    private final EntitlementService entitlementService;
    private final BillingPaymentInstrumentService paymentInstrumentService;
    private final ObjectMapper objectMapper;
    private final PrabhixProperties properties;

    /**
     * Signature is verified before persist; once stored we always acknowledge with 200 so
     * Razorpay does not retry poison events forever.
     */
    @Transactional
    public void receive(String signature, String eventId, byte[] rawBody) {
        if (!com.prabhix.platform.billing.razorpay.RazorpaySignature.verifyWebhook(
                rawBody, signature, properties.billing().razorpay().webhookSecret())) {
            throw ApiException.of(ErrorCode.PAYMENT_SIGNATURE_MISMATCH, "Webhook signature did not match");
        }

        if (eventId != null && !eventId.isBlank()) {
            var existing = webhookEventRepository.findByProviderAndProviderEventId(
                    BillingEnums.WebhookProvider.RAZORPAY, eventId);
            if (existing.isPresent()) {
                return;
            }
        }

        JsonNode root = parsePayload(rawBody);
        String eventType = root.path("event").asText("unknown");
        if (eventId == null || eventId.isBlank()) {
            eventId = root.path("id").asText(null);
        }

        BillingWebhookEvent event = new BillingWebhookEvent();
        event.setProvider(BillingEnums.WebhookProvider.RAZORPAY);
        event.setProviderEventId(eventId);
        event.setEventType(eventType);
        event.setPayload(objectMapper.convertValue(root, Map.class));
        event.setSignature(signature);
        event.setSignatureVerified(true);
        event.setStatus(BillingEnums.WebhookEventStatus.PENDING);
        event.setReceivedAt(Instant.now());
        event = webhookEventRepository.save(event);

        try {
            processEvent(event, root);
            event.setStatus(BillingEnums.WebhookEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
        } catch (Exception ex) {
            log.error("Webhook processing failed for {}: {}", eventId, ex.getMessage(), ex);
            event.setStatus(BillingEnums.WebhookEventStatus.FAILED);
            event.setLastError(ex.getMessage());
        }
        event.setAttempts(event.getAttempts() + 1);
        webhookEventRepository.save(event);
    }

    @Scheduled(fixedDelayString = "${prabhix.billing.webhook-retry-interval:PT5M}")
    @Transactional
    public void retryFailedEvents() {
        var pending = webhookEventRepository.findTop50ByStatusInOrderByReceivedAtAsc(
                List.of(BillingEnums.WebhookEventStatus.PENDING, BillingEnums.WebhookEventStatus.FAILED));
        for (BillingWebhookEvent event : pending) {
            if (event.getAttempts() >= 10) {
                continue;
            }
            try {
                JsonNode root = objectMapper.valueToTree(event.getPayload());
                processEvent(event, root);
                event.setStatus(BillingEnums.WebhookEventStatus.PROCESSED);
                event.setProcessedAt(Instant.now());
                event.setLastError(null);
            } catch (Exception ex) {
                event.setStatus(BillingEnums.WebhookEventStatus.FAILED);
                event.setLastError(ex.getMessage());
            }
            event.setAttempts(event.getAttempts() + 1);
            webhookEventRepository.save(event);
        }
    }

    void processEvent(BillingWebhookEvent event, JsonNode root) {
        String eventType = event.getEventType();
        JsonNode entity = root.path("payload").path("payment").has("entity")
                ? root.path("payload").path("payment").path("entity")
                : root.path("payload").path("order").path("entity");

        switch (eventType) {
            case "payment.captured" -> handlePaymentCaptured(event, entity);
            case "payment.failed" -> handlePaymentFailed(event, entity);
            case "order.paid" -> handleOrderPaid(event, root.path("payload").path("order").path("entity"));
            case "refund.processed" -> handleRefundProcessed(event, entity);
            case "subscription.charged" -> handleSubscriptionCharged(event, root);
            case "subscription.halted" -> handleSubscriptionHalted(event, root);
            case "subscription.cancelled" -> handleSubscriptionCancelled(event, root);
            default -> event.setStatus(BillingEnums.WebhookEventStatus.IGNORED);
        }
    }

    private void handlePaymentCaptured(BillingWebhookEvent event, JsonNode payment) {
        String razorpayOrderId = payment.path("order_id").asText();
        BillingOrder order = orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (order == null) {
            return;
        }
        event.setOrganizationId(order.getOrganizationId());
        event.setOrderId(order.getId());

        paymentCompletionService.completeCapture(
                order, PaymentCompletionService.fromGatewayPayment(payment, true));
        paymentInstrumentService.captureFromGatewayPayment(order.getOrganizationId(), payment);
    }

    private void handlePaymentFailed(BillingWebhookEvent event, JsonNode payment) {
        String razorpayOrderId = payment.path("order_id").asText();
        orderRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(order -> {
            event.setOrganizationId(order.getOrganizationId());
            event.setOrderId(order.getId());
            if (order.getStatus() != BillingEnums.OrderStatus.CAPTURED) {
                order.setStatus(BillingEnums.OrderStatus.FAILED);
                order.setFailureReason(payment.path("error_description").asText("Payment failed"));
                orderRepository.save(order);
            }

            subscriptionRepository.findByOrganizationIdAndStatusIn(order.getOrganizationId(),
                            List.of(BillingEnums.SubscriptionStatus.ACTIVE,
                                    BillingEnums.SubscriptionStatus.PAST_DUE))
                    .ifPresent(sub -> {
                        sub.setStatus(BillingEnums.SubscriptionStatus.PAST_DUE);
                        sub.setFailedPaymentCount(sub.getFailedPaymentCount() + 1);
                        sub.setGracePeriodEndsAt(DunningSchedule.nextRetryAt(sub.getFailedPaymentCount()));
                        sub.setNextDunningRetryAt(sub.getGracePeriodEndsAt());
                        subscriptionRepository.save(sub);
                        entitlementService.evictCache(order.getOrganizationId());
                    });
        });
    }

    private void handleOrderPaid(BillingWebhookEvent event, JsonNode orderNode) {
        String razorpayOrderId = orderNode.path("id").asText();
        orderRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(order -> {
            event.setOrganizationId(order.getOrganizationId());
            event.setOrderId(order.getId());
            if (order.getStatus() != BillingEnums.OrderStatus.CAPTURED) {
                String paymentId = order.getRazorpayPaymentId();
                PaymentCompletionService.PaymentCaptureDetails capture =
                        new PaymentCompletionService.PaymentCaptureDetails(
                                paymentId,
                                order.getTotalPaise(),
                                order.getCurrency(),
                                null, null, null, null, null,
                                false);
                paymentCompletionService.completeCapture(order, capture);
            }
        });
    }

    private void handleRefundProcessed(BillingWebhookEvent event, JsonNode payment) {
        paymentRepository.findByRazorpayPaymentId(payment.path("id").asText()).ifPresent(bp -> {
            event.setOrganizationId(bp.getOrganizationId());
            event.setOrderId(bp.getOrderId());
            bp.setStatus(BillingEnums.PaymentStatus.REFUNDED);
            bp.setRefundedPaise(payment.path("amount_refunded").asLong(bp.getAmountPaise()));
            paymentRepository.save(bp);
        });
    }

    private void handleSubscriptionCharged(BillingWebhookEvent event, JsonNode root) {
        JsonNode subNode = root.path("payload").path("subscription").path("entity");
        subscriptionRepository.findByRazorpaySubscriptionId(subNode.path("id").asText())
                .ifPresent(sub -> {
                    event.setOrganizationId(sub.getOrganizationId());
                    sub.setStatus(BillingEnums.SubscriptionStatus.ACTIVE);
                    sub.setFailedPaymentCount(0);
                    sub.setGracePeriodEndsAt(null);
                    sub.setLastPaymentAt(Instant.now());
                    subscriptionRepository.save(sub);
                    entitlementService.evictCache(sub.getOrganizationId());
                });
    }

    private void handleSubscriptionHalted(BillingWebhookEvent event, JsonNode root) {
        JsonNode subNode = root.path("payload").path("subscription").path("entity");
        subscriptionRepository.findByRazorpaySubscriptionId(subNode.path("id").asText())
                .ifPresent(sub -> {
                    event.setOrganizationId(sub.getOrganizationId());
                    sub.setStatus(BillingEnums.SubscriptionStatus.PAST_DUE);
                    sub.setGracePeriodEndsAt(Instant.now().plus(7, ChronoUnit.DAYS));
                    subscriptionRepository.save(sub);
                    entitlementService.evictCache(sub.getOrganizationId());
                });
    }

    private void handleSubscriptionCancelled(BillingWebhookEvent event, JsonNode root) {
        JsonNode subNode = root.path("payload").path("subscription").path("entity");
        subscriptionRepository.findByRazorpaySubscriptionId(subNode.path("id").asText())
                .ifPresent(sub -> {
                    event.setOrganizationId(sub.getOrganizationId());
                    sub.setStatus(BillingEnums.SubscriptionStatus.CANCELLED);
                    sub.setCancelledAt(Instant.now());
                    subscriptionRepository.save(sub);
                    entitlementService.evictCache(sub.getOrganizationId());
                });
    }

    private JsonNode parsePayload(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "Webhook payload is not valid JSON");
        }
    }
}
