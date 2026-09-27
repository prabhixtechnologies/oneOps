package com.prabhix.platform.config;

import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.platform.mail.util.LocalMailProfiles;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to boot outside dev/test/local when security-sensitive settings still carry development defaults.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionSecurityStartupValidator {

    static final int MIN_SHARED_SECRET_LENGTH = 32;
    static final String DEFAULT_DB_PASSWORD = "oneops";
    static final String DEV_SIGNING_MARKER = "dev-only-insecure";
    static final String DEV_MAIL_CREDENTIAL_MARKER = "dev-only-mailbox-credential";
    static final String DEV_TRACKING_SECRET = "dev-tracking-secret";

    private final Environment environment;
    private final PrabhixProperties prabhixProperties;
    private final IdentityClientProperties identityProperties;

    @PostConstruct
    void validateProductionConfiguration() {
        if (LocalMailProfiles.isLocal(environment)) {
            log.debug("Skipping production security startup checks (local profile).");
            return;
        }

        List<String> violations = new ArrayList<>();
        checkDatabasePassword(violations);
        checkIdentityServiceToken(violations);
        checkPublicBffCredential(violations);
        checkMailTrackingSecret(violations);
        checkMailCredentialSecret(violations);
        checkLmtpReplayProtection(violations);
        checkSwaggerDisabled(violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with insecure production configuration:\n- "
                            + String.join("\n- ", violations));
        }
    }

    private void checkDatabasePassword(List<String> violations) {
        String password = environment.getProperty("spring.datasource.password");
        if (password != null && DEFAULT_DB_PASSWORD.equalsIgnoreCase(password.trim())) {
            violations.add("spring.datasource.password must not be the default \"" + DEFAULT_DB_PASSWORD + "\"");
        }
    }

    private void checkIdentityServiceToken(List<String> violations) {
        if (!identityInternalFeaturesExpected()) {
            return;
        }
        String token = identityProperties.serviceToken();
        if (isWeakSharedSecret(token)) {
            violations.add("IDENTITY_SERVICE_TOKEN must be set to a strong secret (at least "
                    + MIN_SHARED_SECRET_LENGTH + " characters) when Identity internal URL is configured");
        }
    }

    private void checkPublicBffCredential(List<String> violations) {
        String credential = prabhixProperties.security().publicBffCredential();
        if (isWeakSharedSecret(credential)) {
            violations.add("PUBLIC_BFF_CREDENTIAL (prabhix.security.public-bff-credential) must be set "
                    + "to a strong secret outside dev/test/local");
        }
    }

    private void checkMailTrackingSecret(List<String> violations) {
        if (!prabhixProperties.mail().tracking().enabled()) {
            return;
        }
        String secret = prabhixProperties.mail().tracking().secret();
        if (isWeakSharedSecret(secret) || DEV_TRACKING_SECRET.equalsIgnoreCase(secret.trim())) {
            violations.add("MAIL_TRACKING_SECRET must be a strong non-default value when mail tracking is enabled");
        }
    }

    private void checkMailCredentialSecret(List<String> violations) {
        String secret = prabhixProperties.mail().credentials().secret();
        if (secret == null
                || secret.contains(DEV_MAIL_CREDENTIAL_MARKER)
                || secret.length() < MIN_SHARED_SECRET_LENGTH) {
            violations.add("MAIL_CREDENTIAL_SECRET must be at least " + MIN_SHARED_SECRET_LENGTH
                    + " characters and must not use the development default");
        }
    }

    private void checkLmtpReplayProtection(List<String> violations) {
        var inbound = prabhixProperties.mail().inbound();
        boolean lmtpIngest = inbound.lmtpToken() != null && !inbound.lmtpToken().isBlank();
        if (!lmtpIngest) {
            return;
        }
        if (!inbound.lmtpReplayRequired()) {
            violations.add("MAIL_LMTP_REPLAY_REQUIRED must be true when LMTP ingestion is configured");
        }
        if (isWeakSharedSecret(inbound.lmtpHmacSecret())) {
            violations.add("MAIL_LMTP_HMAC_SECRET must be a strong secret when LMTP ingestion is configured");
        }
    }

    private void checkSwaggerDisabled(List<String> violations) {
        if (booleanProperty("springdoc.swagger-ui.enabled", false)
                || booleanProperty("springdoc.api-docs.enabled", false)) {
            violations.add("SWAGGER_ENABLED must be false outside dev/test/local (disable springdoc API docs and UI)");
        }
    }

    /**
     * Outbound Identity internal API calls, inbound {@code /internal/*} service auth, and provisioning
     * all depend on a shared service token once either side is configured for it.
     */
    boolean identityInternalFeaturesExpected() {
        return identityProperties.internalBaseUrl() != null
                || identityProperties.serviceToken() != null;
    }

    static boolean isWeakSharedSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return true;
        }
        String trimmed = secret.trim();
        if (trimmed.length() < MIN_SHARED_SECRET_LENGTH) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.contains(DEV_SIGNING_MARKER)
                || lower.contains("changeme")
                || lower.contains("password");
    }

    private boolean booleanProperty(String key, boolean defaultValue) {
        String raw = environment.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
