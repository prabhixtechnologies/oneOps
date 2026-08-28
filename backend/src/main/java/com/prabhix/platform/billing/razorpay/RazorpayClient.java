package com.prabhix.platform.billing.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayClient {

    private final PrabhixProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public JsonNode createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        requireConfigured();
        return post("/orders", Map.of(
                "amount", amountPaise,
                "currency", currency,
                "receipt", receipt,
                "notes", notes == null ? Map.of() : notes));
    }

    public JsonNode fetchPayment(String paymentId) {
        requireConfigured();
        return get("/payments/" + paymentId);
    }

    public JsonNode capturePayment(String paymentId, long amountPaise, String currency) {
        requireConfigured();
        return post("/payments/" + paymentId + "/capture", Map.of(
                "amount", amountPaise,
                "currency", currency));
    }

    public JsonNode refundPayment(String paymentId, long amountPaise) {
        requireConfigured();
        return post("/payments/" + paymentId + "/refund", Map.of("amount", amountPaise));
    }

    public JsonNode createSubscription(String planId, int quantity, Map<String, String> notes) {
        requireConfigured();
        return post("/subscriptions", Map.of(
                "plan_id", planId,
                "total_count", 120,
                "quantity", quantity,
                "customer_notify", 1,
                "notes", notes == null ? Map.of() : notes));
    }

    public JsonNode cancelSubscription(String subscriptionId, boolean cancelAtCycleEnd) {
        requireConfigured();
        return post("/subscriptions/" + subscriptionId + "/cancel", Map.of(
                "cancel_at_cycle_end", cancelAtCycleEnd ? 1 : 0));
    }

    /**
     * Charges a saved token against an existing order. Used for self-managed renewals and dunning
     * rather than Razorpay-native subscriptions, which would fork the checkout path.
     */
    public JsonNode createRecurringPayment(String email,
                                           String contact,
                                           long amountPaise,
                                           String currency,
                                           String razorpayOrderId,
                                           String customerId,
                                           String tokenId) {
        requireConfigured();
        var payload = new java.util.HashMap<String, Object>();
        payload.put("email", email);
        payload.put("amount", amountPaise);
        payload.put("currency", currency);
        payload.put("order_id", razorpayOrderId);
        payload.put("token", tokenId);
        payload.put("recurring", "1");
        payload.put("description", "Subscription charge");
        if (contact != null && !contact.isBlank()) {
            payload.put("contact", contact);
        }
        if (customerId != null && !customerId.isBlank()) {
            payload.put("customer_id", customerId);
        }
        return post("/payments/create/recurring", payload);
    }

    public JsonNode createCustomer(String email, String contact, String name) {
        requireConfigured();
        var payload = new java.util.HashMap<String, Object>();
        payload.put("email", email);
        payload.put("fail_existing", "0");
        if (contact != null && !contact.isBlank()) {
            payload.put("contact", contact);
        }
        if (name != null && !name.isBlank()) {
            payload.put("name", name);
        }
        return post("/customers", payload);
    }

    private JsonNode get(String path) {
        try {
            String body = restClient.get()
                    .uri(apiBase() + path)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException ex) {
            throw gatewayError(ex);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.PAYMENT_GATEWAY_ERROR, "Payment gateway request failed", ex);
        }
    }

    private JsonNode post(String path, Object payload) {
        try {
            String body = restClient.post()
                    .uri(apiBase() + path)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException ex) {
            throw gatewayError(ex);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.PAYMENT_GATEWAY_ERROR, "Payment gateway request failed", ex);
        }
    }

    private ApiException gatewayError(RestClientResponseException ex) {
        String description = "Payment gateway error";
        try {
            JsonNode error = objectMapper.readTree(ex.getResponseBodyAsString()).path("error");
            if (!error.isMissingNode()) {
                description = error.path("description").asText(description);
                log.warn("Razorpay error {}: {}", error.path("code").asText(), description);
            }
        } catch (Exception parseEx) {
            log.warn("Razorpay error (unparseable body): HTTP {}", ex.getStatusCode().value());
        }
        return ApiException.of(ErrorCode.PAYMENT_GATEWAY_ERROR, "Payment could not be processed");
    }

    private void requireConfigured() {
        if (!properties.billing().razorpay().configured()) {
            throw ApiException.of(ErrorCode.BILLING_NOT_CONFIGURED,
                    "Payment processing is not configured on this environment");
        }
    }

    private String apiBase() {
        return properties.billing().razorpay().apiBase();
    }

    private String basicAuth() {
        var razorpay = properties.billing().razorpay();
        String token = razorpay.keyId() + ":" + razorpay.keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
