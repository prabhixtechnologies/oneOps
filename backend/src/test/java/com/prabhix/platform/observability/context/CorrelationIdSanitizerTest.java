package com.prabhix.platform.observability.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdSanitizerTest {

    @Test
    void acceptsValidHeader() {
        String id = "abc12345-valid_id";
        assertEquals(id, CorrelationIdSanitizer.resolve(id));
    }

    @Test
    void rejectsNewlineInjection() {
        String generated = CorrelationIdSanitizer.resolve("evil\n\"}\n{\"fake");
        assertNotEquals("evil\n\"}\n{\"fake", generated);
        assertTrue(generated.matches("^[A-Za-z0-9._-]{8,64}$"));
    }

    @Test
    void rejectsTooShortValues() {
        assertTrue(CorrelationIdSanitizer.resolve("short").matches("^[A-Za-z0-9._-]{8,64}$"));
    }

    @Test
    void generatesWhenBlank() {
        assertTrue(CorrelationIdSanitizer.resolve(null).length() >= 8);
        assertTrue(CorrelationIdSanitizer.resolve("   ").length() >= 8);
    }
}
