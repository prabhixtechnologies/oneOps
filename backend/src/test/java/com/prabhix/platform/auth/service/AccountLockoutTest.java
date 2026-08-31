package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.DeviceSession;
import com.prabhix.platform.auth.dto.AuthDtos.LoginRequest;
import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.auth.repository.RefreshTokenRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.org.service.ActiveOrganizationResolver;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.security.jwt.JwtService;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.domain.User.UserStatus;
import com.prabhix.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLockoutTest {

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
    void locksAccountAfterRepeatedFailures() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("locked@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(4);

        when(userService.findByEmail("locked@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        LoginRequest request = new LoginRequest("locked@example.com", "wrong", null, null, null);

        assertThrows(ApiException.class, () -> authService.login(request, "127.0.0.1", "JUnit"));
        verify(userService).recordLoginFailure(user);
    }

    @Test
    void rejectsLoginWhenAccountIsLocked() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("locked@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));

        when(userService.findByEmail("locked@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("locked@example.com", "secret", null, null, null);
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(request, "127.0.0.1", "JUnit"));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getCode());
    }
}
