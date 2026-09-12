package com.prabhix.platform.auth;

import com.prabhix.platform.auth.config.AuthProperties;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Refuses local browser credential login when Identity is the auth source of truth.
 */
@Component
@RequiredArgsConstructor
public class LegacyCredentialLoginGuard {

    private final AuthProperties authProperties;
    private final PrabhixProperties properties;

    public void requireLegacyEnabled() {
        if (authProperties.legacyCredentialLogin()) {
            return;
        }
        String issuer = properties.security().identity().issuer();
        String where = (issuer != null && !issuer.isBlank())
                ? " Sign in at " + trimTrailingSlash(issuer) + "."
                : " Use the Identity hosted login.";
        throw ApiException.of(ErrorCode.AUTH_MOVED_TO_IDENTITY,
                "Browser credential login has moved to Prabhix Identity." + where);
    }

    private static String trimTrailingSlash(String issuer) {
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }
}
