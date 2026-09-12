package com.prabhix.platform.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Auth-source switch for this API when Prabhix Identity owns browser sign-in.
 *
 * <p>With {@code legacy-credential-login=false} (the default), password register/login, magic
 * link, OTP, Google SSO, and anonymous password-reset endpoints on this API return 410 so
 * products cannot keep a second login path. Session refresh, cookie exchange, logout, /me, and
 * authenticated change-password remain available.
 */
@Validated
@ConfigurationProperties(prefix = "prabhix.auth")
public record AuthProperties(
        /**
         * When false, browser credential login on this API is gone — clients must use Identity
         * hosted login. Set true only for emergency rollback while Identity is unavailable.
         */
        @DefaultValue("false") boolean legacyCredentialLogin) {
}
