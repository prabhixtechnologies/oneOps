package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Optional LMTP replay protection: bounded timestamp skew and Redis-backed nonce cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LmtpReplayGuard {

    private static final String NONCE_PREFIX = "pbx:mail:lmtp:nonce:";

    private final PrabhixProperties properties;
    private final StringRedisTemplate redis;

    public void verifyIfRequired(String recipient,
                                 String rawMimeBase64,
                                 String timestampHeader,
                                 String nonceHeader,
                                 String signatureHeader) {
        var inbound = properties.mail().inbound();
        if (!inbound.lmtpReplayRequired()) {
            return;
        }
        String secret = inbound.lmtpHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "LMTP replay protection is misconfigured");
        }
        if (timestampHeader == null || timestampHeader.isBlank()
                || nonceHeader == null || nonceHeader.isBlank()
                || signatureHeader == null || signatureHeader.isBlank()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "LMTP signature headers are required");
        }

        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Invalid LMTP timestamp");
        }

        Instant timestamp = Instant.ofEpochSecond(epochSeconds);
        Instant now = Instant.now();
        Duration skew = inbound.lmtpMaxTimestampSkew();
        if (timestamp.isBefore(now.minus(skew)) || timestamp.isAfter(now.plus(skew))) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "LMTP timestamp outside allowed window");
        }

        String expected = sign(secret, canonical(recipient, rawMimeBase64, timestampHeader.trim(), nonceHeader.trim()));
        if (!constantTimeEquals(expected, signatureHeader.trim())) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Invalid LMTP signature");
        }

        String nonceKey = NONCE_PREFIX + nonceHeader.trim();
        try {
            Boolean accepted = redis.opsForValue().setIfAbsent(nonceKey, "1", inbound.lmtpNonceTtl());
            if (accepted == null || !accepted) {
                throw ApiException.of(ErrorCode.FORBIDDEN, "LMTP nonce replay");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("LMTP nonce cache unavailable, rejecting request: {}", ex.getMessage());
            throw ApiException.of(ErrorCode.FORBIDDEN, "LMTP replay protection unavailable");
        }
    }

    static String canonical(String recipient, String rawMimeBase64, String timestamp, String nonce) {
        return recipient + "\n" + rawMimeBase64 + "\n" + timestamp + "\n" + nonce;
    }

    static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
