package com.prabhix.platform.mail.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailWebhookEvent;
import com.prabhix.platform.mail.repository.MailWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SesWebhookService {

    private final MailWebhookEventRepository webhookEventRepository;
    private final SesFeedbackService sesFeedbackService;
    private final SnsMessageVerifier snsMessageVerifier;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Signature is verified before persist; once stored we always acknowledge with 200 so
     * SNS does not retry poison messages forever.
     */
    @Transactional
    public void receive(byte[] rawBody) {
        JsonNode envelope = parseEnvelope(rawBody);
        if (!snsMessageVerifier.verify(envelope, rawBody)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "SNS message signature did not verify");
        }

        String messageId = envelope.path("MessageId").asText(null);
        String type = envelope.path("Type").asText("");

        if ("SubscriptionConfirmation".equals(type)) {
            confirmSubscription(envelope);
            persistEvent(messageId, type, envelope, true, MailEnums.WebhookEventStatus.PROCESSED, null);
            return;
        }

        if (!"Notification".equals(type)) {
            persistEvent(messageId, type, envelope, true, MailEnums.WebhookEventStatus.IGNORED, null);
            return;
        }

        if (messageId != null && !messageId.isBlank()) {
            var existing = webhookEventRepository.findByProviderAndProviderEventId(
                    MailEnums.WebhookProvider.SES_SNS, messageId);
            if (existing.isPresent()) {
                return;
            }
        }

        MailWebhookEvent event = persistEvent(messageId, type, envelope, true,
                MailEnums.WebhookEventStatus.PENDING, null);

        try {
            JsonNode sesNotification = sesFeedbackService.parseSesMessage(envelope.path("Message").asText());
            UUID orgId = sesFeedbackService.processSesNotification(sesNotification);
            event.setOrganizationId(orgId);
            event.setStatus(MailEnums.WebhookEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
        } catch (Exception ex) {
            log.error("SES webhook processing failed for {}: {}", messageId, ex.getMessage(), ex);
            event.setStatus(MailEnums.WebhookEventStatus.FAILED);
            event.setLastError(ex.getMessage());
        }
        event.setAttempts(event.getAttempts() + 1);
        webhookEventRepository.save(event);
    }

    private void confirmSubscription(JsonNode envelope) {
        String subscribeUrl = envelope.path("SubscribeURL").asText(null);
        if (subscribeUrl == null || subscribeUrl.isBlank()) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "SubscriptionConfirmation missing SubscribeURL");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(subscribeUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "SNS subscription confirmation returned HTTP " + response.statusCode());
            }
            log.info("Confirmed SNS subscription for topic {}", envelope.path("TopicArn").asText());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Could not confirm SNS subscription", ex);
        }
    }

    private MailWebhookEvent persistEvent(String messageId,
                                          String eventType,
                                          JsonNode envelope,
                                          boolean verified,
                                          MailEnums.WebhookEventStatus status,
                                          UUID organizationId) {
        MailWebhookEvent event = new MailWebhookEvent();
        event.setProvider(MailEnums.WebhookProvider.SES_SNS);
        event.setProviderEventId(messageId);
        event.setEventType(eventType);
        event.setPayload(objectMapper.convertValue(envelope, Map.class));
        event.setSignature(envelope.path("Signature").asText(null));
        event.setSignatureVerified(verified);
        event.setStatus(status);
        event.setOrganizationId(organizationId);
        event.setReceivedAt(Instant.now());
        if (status == MailEnums.WebhookEventStatus.PROCESSED) {
            event.setProcessedAt(Instant.now());
        }
        return webhookEventRepository.save(event);
    }

    private JsonNode parseEnvelope(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "Webhook payload is not valid JSON");
        }
    }
}
