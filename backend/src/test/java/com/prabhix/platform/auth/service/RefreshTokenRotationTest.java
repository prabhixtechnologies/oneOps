package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.DeviceSession;
import com.prabhix.platform.auth.domain.RefreshToken;
import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.auth.repository.RefreshTokenRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.security.jwt.JwtService;
import com.prabhix.platform.security.jwt.JwtService.IssuedToken;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationTest {

    @Mock private UserService userService;
    @Mock private OrganizationService organizationService;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private PermissionResolver permissionResolver;
    @Mock private DeviceSessionRepository deviceSessionRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private TokenDenyList tokenDenyList;
    @Mock private ApplicationEventPublisher events;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null,
                new PrabhixProperties.Security(
                        new PrabhixProperties.Security.Jwt("secret", "issuer",
                                Duration.ofMinutes(15), Duration.ofDays(30)),
                        null, null),
                null, null, null, null, null);
        authService = new AuthService(
                userService, organizationService, membershipRepository, permissionResolver,
                deviceSessionRepository, refreshTokenRepository, passwordEncoder,
                jwtService, tokenDenyList, properties, events);
    }

    @Test
    void refreshRotatesTokenAndIssuesNewPair() {
        String raw = "refresh-raw-token";
        String hash = AuthService.sha256(raw);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        RefreshToken existing = new RefreshToken();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setSessionId(sessionId);
        existing.setTokenHash(hash);
        existing.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));

        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setFullName("User");

        DeviceSession session = new DeviceSession();
        session.setId(sessionId);
        session.setUserId(userId);

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existing));
        when(userService.requireActive(userId)).thenReturn(user);
        when(deviceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(membershipRepository.findByUserIdAndStatus(any(), any())).thenReturn(List.of());
        when(permissionResolver.resolve(userId, null)).thenReturn(Set.of(Permission.ORG_READ));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.issue(any())).thenReturn(new IssuedToken("access", Instant.now().plusSeconds(900), 900));

        TokenResponse response = authService.refresh(raw);

        assertEquals("access", response.accessToken());
        assert existing.getUsedAt() != null;
        verify(jwtService).issue(any());
    }

    @Test
    void reusedRefreshTokenRevokesSessionsAndThrows() {
        String raw = "stolen-token";
        String hash = AuthService.sha256(raw);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        RefreshToken reused = new RefreshToken();
        reused.setId(UUID.randomUUID());
        reused.setUserId(userId);
        reused.setSessionId(sessionId);
        reused.setTokenHash(hash);
        reused.setUsedAt(Instant.now().minusSeconds(60));
        reused.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));

        DeviceSession session = new DeviceSession();
        session.setId(sessionId);
        session.setUserId(userId);

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findById(reused.getId())).thenReturn(Optional.of(reused));
        when(deviceSessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId))
                .thenReturn(List.of(session));

        ApiException ex = assertThrows(ApiException.class, () -> authService.refresh(raw));
        assertEquals(ErrorCode.TOKEN_REVOKED, ex.getCode());
        verify(tokenDenyList).revokeUser(userId);
        verify(jwtService, never()).issue(any());
    }
}
