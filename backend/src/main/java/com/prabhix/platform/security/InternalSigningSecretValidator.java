package com.prabhix.platform.security;

import com.prabhix.platform.config.PrabhixProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuses to start a non-dev instance with a weak or default internal signing secret.
 *
 * <p>The secret no longer guards a bearer token — identity's RS256 keys do that — but it still keys
 * every chat visitor token and the cipher around every stored DKIM private key. A forgotten
 * {@code INTERNAL_SIGNING_SECRET} in production would let anyone forge a visitor's conversation or,
 * with a database read, sign mail as a customer's domain. That is a hard fail rather than a warning.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalSigningSecretValidator {

    static final int MIN_SECRET_LENGTH = 64;
    static final String DEV_SECRET_MARKER = "dev-only-insecure";

    private final PrabhixProperties properties;
    private final Environment environment;

    @PostConstruct
    void validateSecretStrength() {
        String secret = properties.security().internalSigningSecret();
        boolean devProfile = List.of(environment.getActiveProfiles()).contains("dev")
                || environment.getActiveProfiles().length == 0;

        if (devProfile) {
            if (secret.contains(DEV_SECRET_MARKER)) {
                log.warn("Using the built-in development signing secret. Never do this outside dev.");
            }
            return;
        }

        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "INTERNAL_SIGNING_SECRET must be at least " + MIN_SECRET_LENGTH + " characters outside dev");
        }
        if (secret.contains(DEV_SECRET_MARKER)) {
            throw new IllegalStateException("INTERNAL_SIGNING_SECRET is still the development default");
        }
    }
}
