package com.prabhix.platform.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prabhix.platform.auth.dto.AuthDtos.GoogleSsoRequest;
import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.user.domain.AuthIdentity;
import com.prabhix.platform.user.domain.AuthIdentity.AuthProvider;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.AuthIdentityRepository;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleSsoService {

    private final AuthIdentityRepository authIdentityRepository;
    private final UserService userService;
    private final AuthService authService;
    private final RestClient restClient = RestClient.create();

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    @Transactional
    public TokenResponse authenticate(GoogleSsoRequest request, String ipAddress, String userAgent) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw ApiException.of(ErrorCode.FEATURE_DISABLED, "Google sign-in is not enabled");
        }

        JsonNode tokenInfo = restClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", request.idToken())
                .retrieve()
                .body(JsonNode.class);

        if (tokenInfo == null) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "That Google token is not valid");
        }

        String audience = text(tokenInfo, "aud");
        if (!googleClientId.equals(audience)) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "That Google token is not valid");
        }

        if (!"true".equalsIgnoreCase(text(tokenInfo, "email_verified"))) {
            throw ApiException.of(ErrorCode.EMAIL_NOT_VERIFIED, "Your Google email is not verified");
        }

        String subject = text(tokenInfo, "sub");
        String email = text(tokenInfo, "email");
        String name = text(tokenInfo, "name");
        if (email == null || email.isBlank()) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "That Google token is not valid");
        }

        Optional<AuthIdentity> existing =
                authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject);

        User user;
        if (existing.isPresent()) {
            AuthIdentity identity = existing.get();
            identity.setLastLoginAt(Instant.now());
            identity.setProviderEmail(email);
            authIdentityRepository.save(identity);
            user = userService.requireActive(identity.getUserId());
        } else {
            user = userService.findByEmail(email)
                    .orElseGet(() -> userService.createPasswordlessUser(email,
                            name != null && !name.isBlank() ? name : email));

            AuthIdentity identity = new AuthIdentity();
            identity.setUserId(user.getId());
            identity.setProvider(AuthProvider.GOOGLE);
            identity.setProviderSubject(subject);
            identity.setProviderEmail(email);
            identity.setRawProfile(toProfileMap(tokenInfo));
            identity.setLastLoginAt(Instant.now());
            authIdentityRepository.save(identity);
        }

        userService.resetLoginFailures(user);
        return authService.completeSignIn(user, request.deviceId(), request.deviceName(),
                request.deviceType(), ipAddress, userAgent);
    }

    private Map<String, Object> toProfileMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        node.fields().forEachRemaining(entry -> map.put(entry.getKey(), entry.getValue().asText()));
        return map;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
