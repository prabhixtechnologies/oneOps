package com.prabhix.platform.mail.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** HMAC-SHA256 tokens for open/click tracking — no PII embedded. */
public final class MailTrackingToken {

    private MailTrackingToken() {
    }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    public static boolean verify(String secret, String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(secret, payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public static String buildToken(String secret, String purpose, String outboxId) {
        String payload = purpose + ":" + outboxId;
        return payload + "." + sign(secret, payload);
    }

    public static String parsePayload(String token) {
        int dot = token.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return token.substring(0, dot);
    }

    public static String parseSignature(String token) {
        int dot = token.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return token.substring(dot + 1);
    }
}
