package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.DeviceSession;
import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.config.PrabhixProperties.Security.SessionCookie;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The browser half of a session: an opaque cookie on the parent domain that any console hostname can
 * exchange for an access token.
 *
 * <p>This exists because the console used to hold a refresh token in {@code localStorage}, which is
 * scoped to one origin. Two apps on two hostnames could not share it, and sharing the refresh token
 * itself would have been worse than useless: refresh tokens rotate, and presenting a used one is
 * treated as theft, so the two apps would have raced each other into revoking the session. A
 * credential that does not rotate can be exchanged concurrently by both apps as often as they like.
 *
 * <p>Cookie handling lives here rather than in {@link AuthService} so that servlet types stay out of
 * the service that decides who is allowed to sign in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCookieService {

    private final DeviceSessionRepository deviceSessionRepository;
    private final AuthService authService;
    private final PrabhixProperties properties;

    /**
     * Binds a fresh cookie to a session and writes it to the response.
     *
     * <p>Called on every flow that establishes a session. Overwriting any previous value is
     * intentional: signing in again on the same device should invalidate the credential the last
     * sign-in handed out.
     *
     * <p>Failure is swallowed on purpose. This is an enhancement to a sign-in that has already
     * succeeded — the caller is holding a valid access token and refresh token by now — so a write
     * failure here should cost the user the shared session, not the login.
     */
    @Transactional
    public void issue(HttpServletResponse response, UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            String raw = Ids.token();
            Instant expiresAt = Instant.now().plus(cookieTtl());

            DeviceSession session = deviceSessionRepository.findById(sessionId).orElse(null);
            if (session == null || !session.isActive()) {
                return;
            }
            session.setCookieTokenHash(AuthService.sha256(raw));
            session.setCookieExpiresAt(expiresAt);
            deviceSessionRepository.save(session);

            response.addHeader(HttpHeaders.SET_COOKIE, build(raw, cookieTtl()).toString());
        } catch (RuntimeException ex) {
            log.warn("Could not establish the shared session cookie for session {}: {}",
                    sessionId, ex.getMessage());
        }
    }

    /**
     * Exchanges the cookie on the request for a short-lived access token.
     *
     * <p>Nothing is rotated or consumed, so two hostnames calling this at the same moment both
     * succeed. A cookie that no longer resolves is cleared from the browser as a side effect, so a
     * signed-out or expired session stops re-presenting a credential that can never work again.
     */
    public TokenResponse exchange(HttpServletRequest request, HttpServletResponse response) {
        String raw = read(request).orElse(null);
        if (raw == null) {
            throw AuthService.noSessionCookie();
        }
        try {
            return authService.exchangeSessionCookie(raw);
        } catch (RuntimeException ex) {
            clear(response);
            throw ex;
        }
    }

    /** Drops the cookie from the browser. Does not touch the row; logout already revokes it. */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = cookie().name();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        SessionCookie config = cookie();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(config.name(), value)
                // Unreadable from JavaScript. This is the whole security gain over localStorage:
                // an injected script can still call the API as the user, but it cannot exfiltrate a
                // credential that outlives the page it is running on.
                .httpOnly(true)
                .secure(config.secure())
                .path("/")
                .maxAge(maxAge)
                // Lax, not None: the cookie is withheld from cross-site POSTs, so the exchange
                // endpoint cannot be driven from another origin. Every console hostname shares one
                // registrable domain, so same-site covers all of them and None is not needed.
                .sameSite(config.sameSite());

        String domain = config.domainOrNull();
        if (domain != null) {
            builder.domain(domain);
        }
        return builder.build();
    }

    private SessionCookie cookie() {
        return properties.security().sessionCookie();
    }

    /** One knob for "how long does a signed-in browser stay signed in", shared with refresh tokens. */
    private Duration cookieTtl() {
        return properties.security().jwt().refreshTokenTtl();
    }
}
