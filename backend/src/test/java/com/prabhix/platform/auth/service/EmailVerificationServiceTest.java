package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.AuthChallenge;
import com.prabhix.platform.auth.domain.AuthChallenge.ChallengePurpose;
import com.prabhix.platform.auth.dto.AuthDtos.EmailVerifyConfirmRequest;
import com.prabhix.platform.auth.repository.AuthChallengeRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.MailRequested;
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
class EmailVerificationServiceTest {

    @Mock private AuthChallengeRepository challengeRepository;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher events;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                new PrabhixProperties.Urls("http://localhost:3000", "http://localhost:5173",
                        "http://localhost:8080"),
                null, null,
                new PrabhixProperties.Otp(6, Duration.ofMinutes(10), 3),
                null, null, null, null);
        service = new EmailVerificationService(challengeRepository, userService, events, properties);
    }

    @Test
    void requestSendsChallengeForUnverifiedUser() {
        User user = unverifiedUser();
        when(userService.requireActive(user.getId())).thenReturn(user);

        service.requestVerification(user.getId(), "127.0.0.1");

        verify(challengeRepository).save(any(AuthChallenge.class));
        verify(events).publishEvent(any(MailRequested.class));
    }

    @Test
    void requestRejectsAlreadyVerifiedUser() {
        User user = unverifiedUser();
        user.setEmailVerifiedAt(Instant.now());
        when(userService.requireActive(user.getId())).thenReturn(user);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.requestVerification(user.getId(), "127.0.0.1"));
        assertEquals(ErrorCode.EMAIL_ALREADY_VERIFIED, ex.getCode());
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void confirmMarksEmailVerified() {
        String rawToken = "verify-token";
        AuthChallenge challenge = challenge(rawToken, Instant.now().plus(Duration.ofMinutes(5)));
        User user = unverifiedUser();
        user.setId(challenge.getUserId());

        when(challengeRepository.findBySecretHashAndPurposeAndConsumedAtIsNull(
                AuthService.sha256(rawToken), ChallengePurpose.EMAIL_VERIFY))
                .thenReturn(Optional.of(challenge));
        when(userService.requireActive(user.getId())).thenReturn(user);

        service.confirmVerification(new EmailVerifyConfirmRequest(rawToken));

        verify(userService).markEmailVerified(user.getId());
        ArgumentCaptor<AuthChallenge> captor = ArgumentCaptor.forClass(AuthChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertEquals(Instant.class, captor.getValue().getConsumedAt().getClass());
    }

    @Test
    void confirmRejectsExpiredToken() {
        String rawToken = "expired-token";
        AuthChallenge challenge = challenge(rawToken, Instant.now().minus(Duration.ofMinutes(1)));
        when(challengeRepository.findBySecretHashAndPurposeAndConsumedAtIsNull(
                AuthService.sha256(rawToken), ChallengePurpose.EMAIL_VERIFY))
                .thenReturn(Optional.of(challenge));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirmVerification(new EmailVerifyConfirmRequest(rawToken)));
        assertEquals(ErrorCode.OTP_EXPIRED, ex.getCode());
    }

    @Test
    void confirmRejectsWrongToken() {
        when(challengeRepository.findBySecretHashAndPurposeAndConsumedAtIsNull(
                any(), any())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirmVerification(new EmailVerifyConfirmRequest("wrong")));
        assertEquals(ErrorCode.TOKEN_INVALID, ex.getCode());
    }

    private User unverifiedUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setFullName("Test User");
        return user;
    }

    private AuthChallenge challenge(String rawToken, Instant expiresAt) {
        AuthChallenge challenge = new AuthChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setPurpose(ChallengePurpose.EMAIL_VERIFY);
        challenge.setUserId(UUID.randomUUID());
        challenge.setDestination("user@example.com");
        challenge.setSecretHash(AuthService.sha256(rawToken));
        challenge.setExpiresAt(expiresAt);
        challenge.setMaxAttempts(3);
        challenge.setAttempts(0);
        return challenge;
    }
}
