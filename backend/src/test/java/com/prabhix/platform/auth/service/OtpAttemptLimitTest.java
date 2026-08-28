package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.AuthChallenge;
import com.prabhix.platform.auth.domain.AuthChallenge.ChallengePurpose;
import com.prabhix.platform.auth.dto.AuthDtos.OtpVerifyRequest;
import com.prabhix.platform.auth.repository.AuthChallengeRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpAttemptLimitTest {

    @Mock private AuthChallengeRepository challengeRepository;
    @Mock private UserService userService;
    @Mock private AuthService authService;
    @Mock private ApplicationEventPublisher events;

    private PasswordlessAuthService passwordlessAuthService;

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null,
                new PrabhixProperties.Otp(6, Duration.ofMinutes(10), 3),
                null, null, null, null);
        passwordlessAuthService = new PasswordlessAuthService(
                challengeRepository, userService, authService, events, properties);
    }

    @Test
    void incrementsAttemptsOnWrongCodeAndEnforcesLimit() {
        AuthChallenge challenge = activeChallenge("123456");
        when(challengeRepository.findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user@example.com", ChallengePurpose.EMAIL_OTP))
                .thenReturn(Optional.of(challenge));

        OtpVerifyRequest request = new OtpVerifyRequest("user@example.com", "000000");

        ApiException first = assertThrows(ApiException.class,
                () -> passwordlessAuthService.verifyOtp(request));
        assertEquals(ErrorCode.OTP_INVALID, first.getCode());
        assertEquals(1, challenge.getAttempts());

        challenge.setAttempts(2);
        ApiException second = assertThrows(ApiException.class,
                () -> passwordlessAuthService.verifyOtp(request));
        assertEquals(ErrorCode.OTP_ATTEMPTS_EXCEEDED, second.getCode());

        verify(challengeRepository, org.mockito.Mockito.atLeastOnce()).save(any(AuthChallenge.class));
        verify(authService, never()).issueTokensForUser(any(), any());
    }

    @Test
    void acceptsCorrectCodeWhenAttemptsRemain() {
        AuthChallenge challenge = activeChallenge("123456");
        when(challengeRepository.findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user@example.com", ChallengePurpose.EMAIL_OTP))
                .thenReturn(Optional.of(challenge));

        User user = new User();
        user.setId(UUID.randomUUID());
        when(userService.requireActive(challenge.getUserId())).thenReturn(user);

        passwordlessAuthService.verifyOtp(new OtpVerifyRequest("user@example.com", "123456"));

        ArgumentCaptor<AuthChallenge> captor = ArgumentCaptor.forClass(AuthChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertEquals(Instant.class, captor.getValue().getConsumedAt().getClass());
    }

    private AuthChallenge activeChallenge(String code) {
        AuthChallenge challenge = new AuthChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setPurpose(ChallengePurpose.EMAIL_OTP);
        challenge.setUserId(UUID.randomUUID());
        challenge.setDestination("user@example.com");
        challenge.setSecretHash(AuthService.sha256(code));
        challenge.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        challenge.setMaxAttempts(3);
        challenge.setAttempts(0);
        return challenge;
    }
}
