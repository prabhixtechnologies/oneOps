package com.prabhix.platform.mail.inbound;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Detects RFC 3464 delivery-status notifications and extracts the report body. */
final class DsnDetector {

    private DsnDetector() {
    }

    static boolean isDeliveryStatusNotification(MimeMessage message, MimeParser.ParsedMime parsed)
            throws MessagingException {
        if (isDaemonFrom(parsed.getFrom())) {
            return true;
        }
        String contentType = message.getContentType();
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("multipart/report")
                && lower.contains("report-type=delivery-status");
    }

    static String extractReportBody(MimeMessage message) throws MessagingException, IOException {
        StringBuilder body = new StringBuilder();
        collectReportParts(message, body);
        return body.toString();
    }

    private static void collectReportParts(Part part, StringBuilder body)
            throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collectReportParts(multipart.getBodyPart(i), body);
            }
            return;
        }
        if (part.isMimeType("message/delivery-status") || part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String text) {
                body.append(text).append('\n');
            } else if (content instanceof java.io.InputStream in) {
                body.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
    }

    private static boolean isDaemonFrom(String from) {
        if (from == null || from.isBlank()) {
            return false;
        }
        String local = from.contains("@") ? from.substring(0, from.indexOf('@')) : from;
        local = local.toLowerCase(Locale.ROOT);
        return local.equals("mailer-daemon") || local.equals("postmaster");
    }
}
