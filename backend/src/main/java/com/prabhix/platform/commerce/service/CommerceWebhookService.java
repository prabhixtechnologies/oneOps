package com.prabhix.platform.commerce.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.billing.razorpay.RazorpaySignature;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceWebhookEvent;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.domain.CommerceEnums.WebhookEventStatus;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.CommerceWebhookEventRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommerceWebhookService {

    private final CommerceWebhookEventRepository webhookEventRepository;
    private final CommerceOrderRepository orderRepository;
    private final CommercePaymentCompletionService paymentCompletionService;
    private final CommerceOrderProvisioningService provisioningService;
    private final ObjectMapper objectMapper;
    private final PrabhixProperties properties;
    private final StructuredEventLogger eventLogger;

    @Transactional
    public void receive(String signature, String eventId, byte[] rawBody) {
        if (!RazorpaySignature.verifyWebhook(
                rawBody, signature, properties.billing().razorpay().webhookSecret())) {
            throw ApiException.of(ErrorCode.PAYMENT_SIGNATURE_MISMATCH, "Webhook signature did not match");
        }
        if (eventId != null && !eventId.isBlank()) {
            var existing = webhookEventRepository.findByProviderAndProviderEventId("RAZORPAY", eventId);
            if (existing.isPresent()) {
                return;
            }
        }
        JsonNode root = parsePayload(rawBody);
        String eventType = root.path("event").asText("unknown");
        if (eventId == null || eventId.isBlank()) {
            eventId = root.path("id").asText(null);
        }

        CommerceWebhookEvent event = new CommerceWebhookEvent();
        event.setProvider("RAZORPAY");
        event.setProviderEventId(eventId);
        event.setEventType(eventType);
        event.setPayload(objectMapper.convertValue(root, Map.class));
        event.setSignature(signature);
        event.setSignatureVerified(true);
        event.setStatus(WebhookEventStatus.PENDING);
        event.setReceivedAt(Instant.now());
        event = webhookEventRepository.save(event);
        eventLogger.log(LogEventCode.COMMERCE_WEBHOOK_RECEIVED, Map.of(
                "eventType", eventType, "providerEventId", eventId == null ? "" : eventId));

        try {
            processEvent(event, root);
            event.setStatus(WebhookEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
        } catch (Exception ex) {
            log.error("Commerce webhook processing failed for {}: {}", eventId, ex.getMessage(), ex);
            event.setStatus(WebhookEventStatus.FAILED);
            event.setLastError(ex.getMessage());
        }
        event.setAttempts(event.getAttempts() + 1);
        webhookEventRepository.save(event);
    }

    void processEvent(CommerceWebhookEvent event, JsonNode root) {
        if (!"payment.captured".equals(event.getEventType())) {
            event.setStatus(WebhookEventStatus.IGNORED);
            return;
        }
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String razorpayOrderId = payment.path("order_id").asText(null);
        if (razorpayOrderId == null) {
            return;
        }
        CommerceOrder order = orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (order == null) {
            event.setStatus(WebhookEventStatus.IGNORED);
            return;
        }
        event.setOrganizationId(order.getOrganizationId());
        event.setOrderId(order.getId());
        paymentCompletionService.completeCapture(order, new CommercePaymentCompletionService.CaptureDetails(
                payment.path("id").asText(),
                payment.path("amount").asLong(order.getTotalMinor()),
                payment.path("currency").asText(order.getCurrency()),
                payment.path("method").asText(null),
                true));
        provisioningService.captureTokenFromPayment(order, payment);
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            order.setStatus(OrderStatus.PAID);
        }
    }

    private JsonNode parsePayload(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "Webhook payload is not valid JSON", ex);
        }
    }
}
