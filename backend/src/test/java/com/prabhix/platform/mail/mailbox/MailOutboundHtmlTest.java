package com.prabhix.platform.mail.mailbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailOutboundHtmlTest {

    @Test
    void stripsScriptFromOutboundHtml() {
        String cleaned = MailOutboundHtml.sanitize("<p>Hi</p><script>alert(1)</script>");
        assertFalse(cleaned.toLowerCase().contains("script"));
        assertTrue(cleaned.contains("Hi"));
    }

    @Test
    void appendSignatureSkipsWhenBodyAlreadyContainsIt() {
        String signature = "<p>Best,<br>Support</p>";
        String body = "<p>Hello</p><p>Best, Support</p>";
        assertTrue(MailOutboundHtml.bodyAlreadyContainsSignature(body, signature));
        String merged = MailOutboundHtml.appendSignature(body, signature);
        assertTrue(merged.contains("Hello"));
        assertFalse(merged.split("Best").length > 3);
    }

    @Test
    void appendSignatureAddsWhenMissing() {
        String signature = "<p>Cheers</p>";
        String merged = MailOutboundHtml.appendSignature("<p>Hi</p>", signature);
        assertTrue(merged.contains("Cheers"));
    }
}
