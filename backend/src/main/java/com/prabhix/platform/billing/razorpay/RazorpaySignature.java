package com.prabhix.platform.billing.razorpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class RazorpaySignature {

    private RazorpaySignature() {
    }

    /** Checkout return path: HMAC(order_id + "|" + payment_id, key_secret). */
    public static boolean verifyCheckout(String orderId, String paymentId, String signature, String keySecret) {
        if (orderId == null || paymentId == null || signature == null || keySecret == null) {
            return false;
        }
        String payload = orderId + "|" + paymentId;
        return constantTimeEquals(hmacSha256Hex(payload, keySecret), signature);
    }

    /** Webhook: HMAC(raw_request_body, webhook_secret) compared to X-Razorpay-Signature. */
    public static boolean verifyWebhook(byte[] rawBody, String signature, String webhookSecret) {
        if (rawBody == null || signature == null || webhookSecret == null) {
            return false;
        }
        String computed = hmacSha256Hex(new String(rawBody, StandardCharsets.UTF_8), webhookSecret);
        return constantTimeEquals(computed, signature);
    }

    public static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
