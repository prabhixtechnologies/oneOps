package com.prabhix.platform.billing.razorpay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpaySignatureTest {

    private static final String KEY_SECRET = "thisisasecret";

    @Test
    void checkoutSignature_knownVector() {
        String orderId = "order_EeRzM9C1PQP7yJ";
        String paymentId = "pay_EeRzbYgnGQC7hF";
        String expected = RazorpaySignature.hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET);

        assertTrue(RazorpaySignature.verifyCheckout(orderId, paymentId, expected, KEY_SECRET));
    }

    @Test
    void checkoutSignature_rejectsTamperedPaymentId() {
        String orderId = "order_EeRzM9C1PQP7yJ";
        String paymentId = "pay_EeRzbYgnGQC7hF";
        String signature = RazorpaySignature.hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET);

        assertFalse(RazorpaySignature.verifyCheckout(orderId, "pay_tampered", signature, KEY_SECRET));
    }

    @Test
    void webhookSignature_knownVector() {
        String body = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String secret = "webhook_secret_123";
        String expected = RazorpaySignature.hmacSha256Hex(body, secret);

        assertTrue(RazorpaySignature.verifyWebhook(body.getBytes(), expected, secret));
    }

    @Test
    void webhookSignature_rejectsTamperedBody() {
        String body = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String secret = "webhook_secret_123";
        String signature = RazorpaySignature.hmacSha256Hex(body, secret);

        assertFalse(RazorpaySignature.verifyWebhook("{\"event\":\"payment.failed\"}".getBytes(),
                signature, secret));
    }
}
