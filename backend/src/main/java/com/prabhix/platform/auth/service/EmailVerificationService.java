package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.AuthChallenge;
import com.prabhix.platform.auth.domain.AuthChallenge.ChallengePurpose;
import com.prabhix.platform.auth.dto.AuthDtos.AckResponse;
import com.prabhix.platform.auth.dto.AuthDtos.EmailVerifyConfirmRequest;
import com.prabhix.platform.auth.repository.AuthChallengeRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.MailRequested;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final AckResponse REQUEST_ACK =
            new AckResponse("If your address is not yet verified, you will receive an email shortly.");

    private final AuthChallengeRepository challengeRepository;
    private final UserService userService;
    private final ApplicationEventPublisher events;
    private final PrabhixProperties properties;

    @Transactional
    public AckResponse requestVerification(UUID userId, String ipAddress) {
        User user = userService.requireActive(userId);
        if (user.getEmailVerifiedAt() != null) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_VERIFIED, "That email is already verified");
        }

        String rawToken = Ids.token();
        AuthChallenge challenge = new AuthChallenge();
        challenge.setPurpose(ChallengePurpose.EMAIL_VERIFY);
        challenge.setUserId(user.getId());
        challenge.setDestination(user.getEmail());
        challenge.setSecretHash(AuthService.sha256(rawToken));
        challenge.setExpiresAt(Instant.now().plus(properties.otp().ttl()));
        challenge.setMaxAttempts(properties.otp().maxAttempts());
        challenge.setIpAddress(ipAddress);
        challengeRepository.save(challenge);

        int expiryMinutes = (int) properties.otp().ttl().toMinutes();
        // Console router path, not an API path, and not /auth/verify-email — that route never
        // existed, so every verification email sent so far has 404'd. Same defect the magic-link and
        // password-reset links had. Spelled the way web/src/routes-shell.tsx spells it.
        String link = properties.urls().console() + "/verify-email?token=" + rawToken;
        events.publishEvent(MailRequested.interactive(
                user.getEmail(),
                "auth.email-verify",
                Map.of(
                        "name", user.effectiveDisplayName(),
                        "link", link,
                        "expiryMinutes", expiryMinutes),
                "email-verify:" + user.getEmail() + ":" + challenge.getId()));

        return REQUEST_ACK;
    }

    @Transactional
    public AckResponse confirmVerification(EmailVerifyConfirmRequest request) {
        AuthChallenge challenge = challengeRepository
                .findBySecretHashAndPurposeAndConsumedAtIsNull(
                        AuthService.sha256(request.token()), ChallengePurpose.EMAIL_VERIFY)
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID, "That link is not valid"));

        if (challenge.isExpired()) {
            throw ApiException.of(ErrorCode.OTP_EXPIRED, "That link has expired");
        }
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw ApiException.of(ErrorCode.OTP_ATTEMPTS_EXCEEDED, "Too many attempts");
        }
        if (challenge.getConsumedAt() != null) {
            throw ApiException.of(ErrorCode.CONFLICT, "That challenge was already used");
        }

        User user = userService.requireActive(challenge.getUserId());
        if (user.getEmailVerifiedAt() != null) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_VERIFIED, "That email is already verified");
        }

        challenge.setConsumedAt(Instant.now());
        challengeRepository.save(challenge);
        userService.markEmailVerified(user.getId());

        return new AckResponse("Your email has been verified.");
    }
}
