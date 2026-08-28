package com.prabhix.platform.ai.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiRedactorTest {

    private PiiRedactor redactor;

    @BeforeEach
    void setUp() {
        redactor = new PiiRedactor();
    }

    @Test
    void redactsEmail() {
        var result = redactor.redact("Contact me at user@example.com please");
        assertTrue(result.redacted());
        assertTrue(result.text().contains("[REDACTED_EMAIL]"));
        assertFalse(result.text().contains("user@example.com"));
    }

    @Test
    void redactsIndianPhone() {
        var result = redactor.redact("Call 9876543210 today");
        assertTrue(result.redacted());
        assertTrue(result.text().contains("[REDACTED_PHONE]"));
    }

    @Test
    void redactsPan() {
        var result = redactor.redact("PAN ABCDE1234F submitted");
        assertTrue(result.redacted());
        assertTrue(result.text().contains("[REDACTED_PAN]"));
    }

    @Test
    void redactsAadhaar() {
        var result = redactor.redact("Aadhaar 1234 5678 9012 on file");
        assertTrue(result.redacted());
        assertTrue(result.text().contains("[REDACTED_AADHAAR]"));
    }

    @Test
    void redactsCardNumber() {
        var result = redactor.redact("Card 4111111111111111 expired");
        assertTrue(result.redacted());
        assertTrue(result.text().contains("[REDACTED_CARD]"));
    }

    @Test
    void leavesCleanTextUntouched() {
        var result = redactor.redact("Hello, how can I help?");
        assertFalse(result.redacted());
        assertEquals("Hello, how can I help?", result.text());
    }
}
