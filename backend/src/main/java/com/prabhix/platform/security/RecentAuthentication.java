package com.prabhix.platform.security;

import com.prabhix.identity.client.IdentityToken;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;

/**
 * Sensitive actions require a proof from the last 15 minutes.
 *
 * <p>{@code auth_time} is when the person signed in or stepped up, not when the 15-minute access
 * token was renewed. A quiet afternoon of clicking around does not count.
 */
@Component
public class RecentAuthentication {

    public static final String TOKEN_ATTRIBUTE = "prabhix.identityToken";
    public static final Duration MAX_AGE = Duration.ofMinutes(15);

    public void requireFresh() {
        IdentityToken token = current();
        Instant provedAt = token == null ? null : token.authTime();
        if (provedAt == null || provedAt.isBefore(Instant.now().minus(MAX_AGE))) {
            throw ApiException.of(ErrorCode.STEP_UP_REQUIRED,
                    "Confirm it is you, then try this again.");
        }
    }

    private static IdentityToken current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        Object value = request.getAttribute(TOKEN_ATTRIBUTE);
        return value instanceof IdentityToken token ? token : null;
    }
}
