package com.prabhix.platform.ops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where MobiStack's private admin API lives, for the oneOps BFF.
 *
 * <p>The browser never calls this host. The shared service token in {@code prabhix.identity}
 * authenticates the hop; {@code X-Prabhix-Acting-User} names the staff member.
 *
 * @param internalBaseUrl compose-network origin, no path. Blank disables the client rather than
 *     sending requests into the void.
 */
@ConfigurationProperties(prefix = "prabhix.mobistack")
public record MobiStackAdminProperties(
        String internalBaseUrl,
        @DefaultValue("PT10S") Duration timeout) {

    public MobiStackAdminProperties {
        internalBaseUrl = strip(internalBaseUrl);
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    public boolean enabled() {
        return internalBaseUrl != null;
    }

    private static String strip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
