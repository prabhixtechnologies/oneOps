package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates tracked click targets: trusted hosts redirect directly; others would use an interstitial in the web tier.
 */
@Component
@RequiredArgsConstructor
public class MailTrackingRedirectPolicy {

    private static final Set<String> TRUSTED_SUFFIXES = Set.of(
            "prabhixtechnologies.com",
            "prabhix.com");

    private final PrabhixProperties properties;

    public ResolvedRedirect resolve(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return ResolvedRedirect.fallback(consoleUrl());
        }
        URI uri = parseHttps(targetUrl);
        if (uri == null) {
            return ResolvedRedirect.fallback(consoleUrl());
        }
        if (isTrustedHost(uri.getHost())) {
            return ResolvedRedirect.direct(uri);
        }
        String interstitial = consoleUrl() + "/mail/link-warning?target="
                + java.net.URLEncoder.encode(uri.toString(), java.nio.charset.StandardCharsets.UTF_8);
        return ResolvedRedirect.interstitial(URI.create(interstitial));
    }

    private boolean isTrustedHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (String suffix : TRUSTED_SUFFIXES) {
            if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
                return true;
            }
        }
        String marketing = hostFromUrl(properties.urls().marketing());
        String console = hostFromUrl(properties.urls().console());
        return lower.equals(marketing) || lower.equals(console);
    }

    private static URI parseHttps(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String consoleUrl() {
        String base = properties.urls().console();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String hostFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    public record ResolvedRedirect(URI location, boolean interstitial) {
        static ResolvedRedirect direct(URI location) {
            return new ResolvedRedirect(location, false);
        }

        static ResolvedRedirect interstitial(URI location) {
            return new ResolvedRedirect(location, true);
        }

        static ResolvedRedirect fallback(URI location) {
            return new ResolvedRedirect(location, false);
        }

        static ResolvedRedirect fallback(String url) {
            return fallback(URI.create(url));
        }
    }
}
