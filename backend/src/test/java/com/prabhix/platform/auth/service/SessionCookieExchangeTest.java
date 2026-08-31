package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.DeviceSession;
import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.auth.repository.RefreshTokenRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.org.service.ActiveOrganizationResolver;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.security.jwt.JwtService;
import com.prabhix.platform.security.jwt.JwtService.IssuedToken;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.support.TestProperties;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shared browser session, exchanged for access tokens.
 *
 * <p>The behaviour that matters here is what this does *not* do. A refresh token is single-use, and
 * presenting a used one is treated as theft and revokes the session — which is why two console
 * hostnames could not share one. These tests pin the opposite property: the cookie can be exchanged
 * repeatedly, by however many apps, and nothing about the session changes.
 */
@ExtendWith(MockitoExtension.class)
class SessionCookieExchangeTest {

    @Mock private UserService userService;
    @Mock private OrganizationService organizationService;
    @Mock private ActiveOrganizationResolver activeOrganizations;
    @Mock private PermissionResolver permissionResolver;
    @Mock private DeviceSessionRepository deviceSessionRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private TokenDenyList tokenDenyList;
    @Mock private ApplicationEventPublisher events;
    @Mock private StructuredEventLogger eventLogger;

    private static final String RAW_COOKIE = "opaque-browser-session-token";

    private AuthService authService;

    @BeforeEach
    void setUp() {
        PrabhixProperties properties =
                TestProperties.withSecurity(TestProperties.security(Duration.ofMinutes(15)));
        authService = new AuthService(
                userService, organizationService, permissionResolver, activeOrganizations,
                deviceSessionRepository, refreshTokenRepository, passwordEncoder,
                jwtService, tokenDenyList, properties, events, eventLogger);
    }

    @Test
    void mintsAnAccessTokenWithoutIssuingARefreshToken() {
        UUID userId = UUID.randomUUID();
        DeviceSession session = liveSession(userId);
        stubSession(session);
        stubUser(userId);
        when(jwtService.issue(any())).thenReturn(new IssuedToken("jwt-access-token", Instant.now().plusSeconds(900), 900));

        TokenResponse response = authService.exchangeSessionCookie(RAW_COOKIE);

        assertEquals("jwt-access-token", response.accessToken());
        assertEquals(session.getId(), response.sessionId());
        // No refresh token: a browser does not need one now, and not returning it is what keeps a
        // long-lived credential out of localStorage where injected script could read it.
        assertNull(response.refreshToken());
    }

    @Test
    void canBeExchangedRepeatedlyWithoutInvalidatingItself() {
        UUID userId = UUID.randomUUID();
        DeviceSession session = liveSession(userId);
        String originalHash = session.getCookieTokenHash();
        stubSession(session);
        stubUser(userId);
        when(jwtService.issue(any())).thenReturn(new IssuedToken("jwt-access-token", Instant.now().plusSeconds(900), 900));

        // Two hostnames, or two tabs, exchanging at the same moment.
        assertNotNull(authService.exchangeSessionCookie(RAW_COOKIE).accessToken());
        assertNotNull(authService.exchangeSessionCookie(RAW_COOKIE).accessToken());

        assertEquals(originalHash, session.getCookieTokenHash(),
                "the cookie must not rotate, or the second app's copy would be refused as a replay");
        verify(refreshTokenRepository, never()).save(any());
        verify(tokenDenyList, never()).revokeSession(any());
    }

    @Test
    void refusesACookieBelongingToASignedOutSession() {
        UUID userId = UUID.randomUUID();
        DeviceSession session = liveSession(userId);
        session.setRevokedAt(Instant.now());
        stubSession(session);

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.exchangeSessionCookie(RAW_COOKIE));

        assertEquals(ErrorCode.TOKEN_REVOKED, ex.getCode());
        verify(jwtService, never()).issue(any());
    }

    @Test
    void refusesAnExpiredCookieEvenOnALiveSession() {
        UUID userId = UUID.randomUUID();
        DeviceSession session = liveSession(userId);
        // The browser is free to ignore Max-Age, so expiry is enforced here too.
        session.setCookieExpiresAt(Instant.now().minusSeconds(1));
        stubSession(session);

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.exchangeSessionCookie(RAW_COOKIE));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
        verify(jwtService, never()).issue(any());
    }

    @Test
    void refusesAnUnknownCookie() {
        when(deviceSessionRepository.findByCookieTokenHash(AuthService.sha256(RAW_COOKIE)))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.exchangeSessionCookie(RAW_COOKIE));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
    }

    @Test
    void storesOnlyAHashOfTheCookie() {
        DeviceSession session = liveSession(UUID.randomUUID());

        assertEquals(AuthService.sha256(RAW_COOKIE), session.getCookieTokenHash());
        // Stated explicitly because the whole protection is that a copy of this table yields
        // nothing usable: the raw value must never appear in a column.
        assertEquals(64, session.getCookieTokenHash().length());
    }

    private DeviceSession liveSession(UUID userId) {
        DeviceSession session = new DeviceSession();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setCookieTokenHash(AuthService.sha256(RAW_COOKIE));
        session.setCookieExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        return session;
    }

    private void stubSession(DeviceSession session) {
        when(deviceSessionRepository.findByCookieTokenHash(AuthService.sha256(RAW_COOKIE)))
                .thenReturn(Optional.of(session));
    }

    private void stubUser(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@example.com");
        user.setFullName("Owner");
        when(userService.requireActive(userId)).thenReturn(user);
        when(permissionResolver.resolve(any(), any())).thenReturn(Set.of());
    }
}
