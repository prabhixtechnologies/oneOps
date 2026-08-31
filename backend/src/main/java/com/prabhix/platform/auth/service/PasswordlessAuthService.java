package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.AuthChallenge;
import com.prabhix.platform.auth.domain.AuthChallenge.ChallengePurpose;
import com.prabhix.platform.auth.dto.AuthDtos.AckResponse;
import com.prabhix.platform.auth.dto.AuthDtos.EmailRequest;
import com.prabhix.platform.auth.dto.AuthDtos.MagicLinkVerifyRequest;
import com.prabhix.platform.auth.dto.AuthDtos.OtpVerifyRequest;
import com.prabhix.platform.auth.dto.AuthDtos.PasswordResetRequest;
import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.repository.AuthChallengeRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.common.mail.MailRequest;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordlessAuthService {

    private static final AckResponse GENERIC_ACK =
            new AckResponse("If that address is registered, you will receive an email shortly.");

    private final AuthChallengeRepository challengeRepository;
    private final UserService userService;
    private final AuthService authService;
    private final MailClient mail;
    private final PrabhixProperties properties;

    @Transactional
    public AckResponse requestMagicLink(EmailRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase();
        Optional<User> user = userService.findByEmail(email);

        if (user.isPresent()) {
            String rawToken = Ids.token();
            AuthChallenge challenge = buildChallenge(
                    ChallengePurpose.MAGIC_LINK, user.get().getId(), email, rawToken, ipAddress);
            challengeRepository.save(challenge);

            int expiryMinutes = (int) properties.otp().ttl().toMinutes();
            // Must match the console's router path (web/src/routes.tsx), not the API path. These
            // drifted apart and every emailed magic link 404'd.
            String link = properties.urls().console() + "/magic-link?token=" + rawToken;
            mail.send(MailRequest.interactive(
                    email,
                    "auth.magic-link",
                    Map.of(
                            "name", user.get().effectiveDisplayName(),
                            "link", link,
                            "expiryMinutes", expiryMinutes),
                    "magic:" + email + ":" + challenge.getId()));
        }
        return GENERIC_ACK;
    }

    @Transactional
    public TokenResponse verifyMagicLink(MagicLinkVerifyRequest request) {
        AuthChallenge challenge = consumeChallenge(
                AuthService.sha256(request.token()), ChallengePurpose.MAGIC_LINK);
        User user = userService.requireActive(challenge.getUserId());
        return authService.issueTokensForUser(user, null);
    }

    @Transactional
    public AckResponse requestOtp(EmailRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase();
        Optional<User> user = userService.findByEmail(email);

        if (user.isPresent()) {
            String code = Ids.numericCode(properties.otp().length());
            AuthChallenge challenge = buildChallenge(
                    ChallengePurpose.EMAIL_OTP, user.get().getId(), email, code, ipAddress);
            challengeRepository.save(challenge);

            int expiryMinutes = (int) properties.otp().ttl().toMinutes();
            mail.send(MailRequest.interactive(
                    email,
                    "auth.otp",
                    Map.of("code", code, "expiryMinutes", expiryMinutes),
                    "otp:" + email + ":" + challenge.getId()));
        }
        return GENERIC_ACK;
    }

    @Transactional
    public TokenResponse verifyOtp(OtpVerifyRequest request) {
        String email = request.email().trim().toLowerCase();
        AuthChallenge challenge = challengeRepository
                .findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        email, ChallengePurpose.EMAIL_OTP)
                .orElseThrow(() -> ApiException.of(ErrorCode.OTP_INVALID, "That code is not correct"));

        if (challenge.isExpired()) {
            throw ApiException.of(ErrorCode.OTP_EXPIRED, "That code has expired");
        }
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw ApiException.of(ErrorCode.OTP_ATTEMPTS_EXCEEDED, "Too many attempts. Request a new code.");
        }

        if (!challenge.getSecretHash().equals(AuthService.sha256(request.code()))) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            challengeRepository.save(challenge);
            if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
                throw ApiException.of(ErrorCode.OTP_ATTEMPTS_EXCEEDED, "Too many attempts. Request a new code.");
            }
            throw ApiException.of(ErrorCode.OTP_INVALID, "That code is not correct");
        }

        markConsumed(challenge);
        User user = userService.requireActive(challenge.getUserId());
        return authService.issueTokensForUser(user, null);
    }

    @Transactional
    public AckResponse requestPasswordReset(EmailRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase();
        Optional<User> user = userService.findByEmail(email);

        if (user.isPresent()) {
            String rawToken = Ids.token();
            AuthChallenge challenge = buildChallenge(
                    ChallengePurpose.PASSWORD_RESET, user.get().getId(), email, rawToken, ipAddress);
            challengeRepository.save(challenge);

            int expiryMinutes = (int) properties.otp().ttl().toMinutes();
            String link = properties.urls().console() + "/reset-password?token=" + rawToken;
            mail.send(MailRequest.interactive(
                    email,
                    "auth.magic-link",
                    Map.of(
                            "name", user.get().effectiveDisplayName(),
                            "link", link,
                            "expiryMinutes", expiryMinutes),
                    "reset:" + email + ":" + challenge.getId()));
        }
        return GENERIC_ACK;
    }

    @Transactional
    public AckResponse resetPassword(PasswordResetRequest request) {
        AuthChallenge challenge = consumeChallenge(
                AuthService.sha256(request.token()), ChallengePurpose.PASSWORD_RESET);
        userService.setPassword(challenge.getUserId(), request.newPassword());
        return new AckResponse("Your password has been updated.");
    }

    private AuthChallenge buildChallenge(ChallengePurpose purpose,
                                         java.util.UUID userId,
                                         String destination,
                                         String rawSecret,
                                         String ipAddress) {
        AuthChallenge challenge = new AuthChallenge();
        challenge.setPurpose(purpose);
        challenge.setUserId(userId);
        challenge.setDestination(destination);
        challenge.setSecretHash(AuthService.sha256(rawSecret));
        challenge.setExpiresAt(Instant.now().plus(properties.otp().ttl()));
        challenge.setMaxAttempts(properties.otp().maxAttempts());
        challenge.setIpAddress(ipAddress);
        return challenge;
    }

    private AuthChallenge consumeChallenge(String secretHash, ChallengePurpose purpose) {
        AuthChallenge challenge = challengeRepository
                .findBySecretHashAndPurposeAndConsumedAtIsNull(secretHash, purpose)
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID, "That link is not valid"));

        if (challenge.isExpired()) {
            throw ApiException.of(ErrorCode.OTP_EXPIRED, "That link has expired");
        }
        if (challenge.getAttempts() >= challenge.getMaxAttempts()) {
            throw ApiException.of(ErrorCode.OTP_ATTEMPTS_EXCEEDED, "Too many attempts");
        }

        markConsumed(challenge);
        return challenge;
    }

    private void markConsumed(AuthChallenge challenge) {
        if (challenge.getConsumedAt() != null) {
            throw ApiException.of(ErrorCode.CONFLICT, "That challenge was already used");
        }
        challenge.setConsumedAt(Instant.now());
        challengeRepository.save(challenge);
    }
}
