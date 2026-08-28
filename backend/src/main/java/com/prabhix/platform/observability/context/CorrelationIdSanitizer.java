package com.prabhix.platform.observability.context;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates inbound correlation / request id headers so attacker-controlled values
 * cannot inject newlines or break JSON log structure.
 */
public final class CorrelationIdSanitizer {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private CorrelationIdSanitizer() {
    }

    /**
     * @return sanitised id, or a freshly generated one when the header is absent or hostile
     */
    public static String resolve(String headerValue) {
        if (headerValue == null) {
            return generate();
        }
        String trimmed = headerValue.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64 || !SAFE.matcher(trimmed).matches()) {
            return generate();
        }
        return trimmed;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
