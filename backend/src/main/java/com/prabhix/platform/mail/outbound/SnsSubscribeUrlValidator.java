package com.prabhix.platform.mail.outbound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Ensures SNS subscription confirmations only call AWS-hosted HTTPS endpoints.
 */
final class SnsSubscribeUrlValidator {

    private SnsSubscribeUrlValidator() {
    }

    static void requireAllowedSubscribeUrl(String subscribeUrl) {
        if (subscribeUrl == null || subscribeUrl.isBlank()) {
            throw new IllegalArgumentException("SubscribeURL is missing");
        }
        URI uri;
        try {
            uri = new URI(subscribeUrl.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("SubscribeURL is not a valid URI");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("SubscribeURL must use HTTPS");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("SubscribeURL must include a host");
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".amazonaws.com") || lower.equals("amazonaws.com"))) {
            throw new IllegalArgumentException("SubscribeURL host is not an AWS endpoint");
        }
    }
}
