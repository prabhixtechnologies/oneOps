package com.prabhix.platform.mail.inbound;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MimeParserTest {

    private final MimeParser parser = new MimeParser();

    @Test
    void parsesMultipartAlternative() throws Exception {
        String raw = """
                From: sender@example.com
                To: support@example.com
                Subject: Test multipart
                MIME-Version: 1.0
                Content-Type: multipart/alternative; boundary=bound

                --bound
                Content-Type: text/plain; charset=UTF-8

                Plain body
                --bound
                Content-Type: text/html; charset=UTF-8

                <p>HTML body</p>
                --bound--
                """;
        var parsed = parser.parse(raw.getBytes(StandardCharsets.UTF_8));
        assertNotNull(parsed.getBodyText());
        assertNotNull(parsed.getBodyHtml());
        assertTrue(parsed.getBodyText().contains("Plain body"));
    }

    @Test
    void stripsScriptFromHtml() {
        String dirty = "<p>Hi</p><script>alert(1)</script>";
        String clean = MimeParser.sanitizeHtml(dirty);
        assertFalse(clean.contains("script"));
        assertTrue(clean.contains("Hi"));
    }

    @Test
    void handlesInlineImagePart() throws Exception {
        String raw = """
                From: img@example.com
                To: support@example.com
                Subject: Inline
                MIME-Version: 1.0
                Content-Type: multipart/related; boundary=bound

                --bound
                Content-Type: text/html; charset=UTF-8

                <img src="cid:logo">
                --bound
                Content-Type: image/png
                Content-ID: <logo>
                Content-Disposition: inline; filename=logo.png

                PNGDATA
                --bound--
                """;
        var parsed = parser.parse(raw.getBytes(StandardCharsets.UTF_8));
        assertFalse(parsed.getAttachments().isEmpty());
        assertTrue(parsed.getAttachments().get(0).isInline());
    }
}
