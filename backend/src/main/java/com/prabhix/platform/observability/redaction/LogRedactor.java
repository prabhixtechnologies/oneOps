package com.prabhix.platform.observability.redaction;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strips secrets and sensitive fields before they reach logs or persisted payloads.
 */
public final class LogRedactor {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "secret", "token", "authorization", "api_key", "apikey",
            "access_token", "refresh_token", "card", "cvv", "cvc", "pan", "ssn",
            "credit_card", "card_number", "private_key", "webhook_secret");

    private static final Pattern CARD_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[\\w\\-._~+/]+=*");

    private LogRedactor() {
    }

    public static Map<String, Object> redactMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            if (isSensitiveKey(key)) {
                out.put(key, REDACTED);
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, ?> nestedMap = (Map<String, ?>) nested;
                out.put(key, redactMap(nestedMap));
            } else if (entry.getValue() instanceof String str) {
                out.put(key, redactString(str));
            } else {
                out.put(key, entry.getValue());
            }
        }
        return Map.copyOf(out);
    }

    public static String redactString(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String result = BEARER_PATTERN.matcher(value).replaceAll("Bearer " + REDACTED);
        result = CARD_PATTERN.matcher(result).replaceAll(REDACTED);
        return result;
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalised = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return SENSITIVE_KEYS.stream().anyMatch(normalised::contains);
    }

    public static boolean isSensitivePath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("/auth/")
                || lower.contains("/billing/webhooks")
                || lower.contains("/commerce/webhooks")
                || lower.contains("/payment")
                || lower.contains("/webhook");
    }

    public static boolean isSensitiveHeader(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("authorization")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("api-key")
                || lower.contains("cookie");
    }
}
