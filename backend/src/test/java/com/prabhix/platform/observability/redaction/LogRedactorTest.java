package com.prabhix.platform.observability.redaction;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRedactorTest {

    @Test
    void redactsSensitiveKeys() {
        Map<String, Object> out = LogRedactor.redactMap(Map.of(
                "email", "user@example.com",
                "password", "secret123",
                "nested", Map.of("api_key", "abc")));
        assertEquals("user@example.com", out.get("email"));
        assertEquals(LogRedactor.REDACTED, out.get("password"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) out.get("nested");
        assertEquals(LogRedactor.REDACTED, nested.get("api_key"));
    }

    @Test
    void redactsBearerTokensAndCardNumbers() {
        String redacted = LogRedactor.redactString("Authorization Bearer eyJhbGciOiJIUzI1NiJ9");
        assertTrue(redacted.contains(LogRedactor.REDACTED));
        assertFalse(redacted.contains("eyJhbGciOi"));
        assertTrue(LogRedactor.redactString("card 4111111111111111").contains(LogRedactor.REDACTED));
    }

    @Test
    void detectsSensitivePathsAndHeaders() {
        assertTrue(LogRedactor.isSensitivePath("/api/v1/oneops/auth/login"));
        assertTrue(LogRedactor.isSensitivePath("/api/v1/oneops/billing/webhooks/razorpay"));
        assertTrue(LogRedactor.isSensitiveHeader("Authorization"));
        assertFalse(LogRedactor.isSensitivePath("/api/v1/oneops/members"));
    }
}
