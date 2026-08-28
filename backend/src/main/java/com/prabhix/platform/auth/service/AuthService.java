package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.DeviceSession;
import com.prabhix.platform.auth.domain.DeviceSession.DeviceType;
import com.prabhix.platform.auth.domain.RefreshToken;
import com.prabhix.platform.auth.dto.AuthDtos.AuthMeResponse;
import com.prabhix.platform.auth.dto.AuthDtos.LoginRequest;
import com.prabhix.platform.auth.dto.AuthDtos.RegisterRequest;
import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.auth.repository.RefreshTokenRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.dto.OrgDtos.CreateOrganizationRequest;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.jwt.JwtService;
import com.prabhix.platform.security.jwt.JwtService.IssuedToken;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.domain.User.UserStatus;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final OrganizationService organizationService;
    private final OrganizationMembershipRepository membershipRepository;
    private final PermissionResolver permissionResolver;
    private final DeviceSessionRepository deviceSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenDenyList tokenDenyList;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        User user = userService.createUser(request.email(), request.password(), request.fullName());
        if (request.organizationName() != null && !request.organizationName().isBlank()) {
            organizationService.create(user.getId(),
                    new CreateOrganizationRequest(request.organizationName().trim()));
        }
        return issueTokens(user, null, null, null, null, null);
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Optional<User> optionalUser = userService.findByEmail(request.email());
        if (optionalUser.isEmpty()) {
            events.publishEvent(AuditRequested.failure(null, null, "auth.login.failed",
                    "unknown email"));
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "Email or password is not correct");
        }

        User user = optionalUser.get();
        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.of(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled");
        }
        if (user.isLockedNow()) {
            throw ApiException.of(ErrorCode.ACCOUNT_LOCKED,
                    "This account is temporarily locked. Try again later.");
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            userService.recordLoginFailure(user);
            events.publishEvent(AuditRequested.failure(null, user.getId(), "auth.login.failed",
                    "bad password"));
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "Email or password is not correct");
        }

        userService.resetLoginFailures(user);
        events.publishEvent(AuditRequested.of(null, user.getId(), "auth.login.success",
                "user", user.getId()));

        DeviceType deviceType = parseDeviceType(request.deviceType());
        DeviceSession session = resolveSession(user.getId(), request.deviceId(), request.deviceName(),
                deviceType, userAgent, ipAddress);

        return issueTokens(user, session, ipAddress, userAgent, request.deviceId(), request.deviceName());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID, "That refresh token is not valid"));

        if (token.getUsedAt() != null) {
            handleTokenTheft(token);
            throw ApiException.of(ErrorCode.TOKEN_REVOKED,
                    "This session was revoked because a refresh token was reused");
        }
        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "That refresh token is not valid");
        }

        token.setUsedAt(Instant.now());
        refreshTokenRepository.save(token);

        User user = userService.requireActive(token.getUserId());
        DeviceSession session = deviceSessionRepository.findById(token.getSessionId())
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID, "That session no longer exists"));
        if (!session.isActive()) {
            throw ApiException.of(ErrorCode.TOKEN_REVOKED, "This session was signed out");
        }

        session.setLastSeenAt(Instant.now());
        deviceSessionRepository.save(session);

        UUID orgId = resolveActiveOrganization(user.getId(), user.getDefaultOrganizationId());
        Set<Permission> permissions = permissionResolver.resolve(user.getId(), orgId);

        String newRaw = com.prabhix.platform.common.util.Ids.token();
        RefreshToken successor = createRefreshToken(user.getId(), session.getId(), newRaw);
        token.setReplacedBy(successor.getId());
        refreshTokenRepository.save(token);

        PrabhixPrincipal principal = new PrabhixPrincipal(
                user.getId(), user.getEmail(), user.effectiveDisplayName(),
                orgId, permissions, session.getId(), user.isPlatformAdmin());
        IssuedToken access = jwtService.issue(principal);

        return new TokenResponse(
                access.token(),
                newRaw,
                access.expiresInSeconds(),
                orgId,
                permissions.stream().map(Permission::name).collect(Collectors.toUnmodifiableSet()));
    }

    @Transactional
    public void logout(UUID userId, UUID sessionId, String rawRefreshToken) {
        if (sessionId != null) {
            deviceSessionRepository.findById(sessionId).ifPresent(session -> {
                if (session.getUserId().equals(userId) && session.getRevokedAt() == null) {
                    session.setRevokedAt(Instant.now());
                    session.setRevokedReason("logout");
                    deviceSessionRepository.save(session);
                }
            });
            tokenDenyList.revokeSession(sessionId);
        }
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken)).ifPresent(token -> {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            });
        }
        events.publishEvent(AuditRequested.of(null, userId, "auth.logout", "user", userId));
    }

    @Transactional
    public TokenResponse selectOrganization(UUID userId, UUID organizationId, UUID sessionId) {
        organizationService.requireActiveMembership(organizationId, userId);
        User user = userService.requireActive(userId);
        userService.setDefaultOrganization(userId, organizationId);

        DeviceSession session = sessionId != null
                ? deviceSessionRepository.findById(sessionId).orElse(null)
                : null;
        if (session != null && session.isActive()) {
            session.setLastSeenAt(Instant.now());
            deviceSessionRepository.save(session);
        }

        return issueTokens(user, session, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public AuthMeResponse currentUser(PrabhixPrincipal principal) {
        return new AuthMeResponse(
                principal.userId(),
                principal.email(),
                principal.displayName(),
                principal.organizationId(),
                principal.sessionId(),
                principal.permissions().stream().map(Permission::name).collect(Collectors.toUnmodifiableSet()),
                principal.platformAdmin());
    }

    @Transactional
    public TokenResponse issueTokensForUser(User user, DeviceSession session) {
        return issueTokens(user, session, null, null, null, null);
    }

    @Transactional
    public TokenResponse completeSignIn(User user,
                                        String deviceId,
                                        String deviceName,
                                        String deviceType,
                                        String ipAddress,
                                        String userAgent) {
        DeviceType type = parseDeviceType(deviceType);
        DeviceSession session = resolveSession(user.getId(), deviceId, deviceName, type, userAgent, ipAddress);
        return issueTokens(user, session, ipAddress, userAgent, deviceId, deviceName);
    }

    private TokenResponse issueTokens(User user,
                                      DeviceSession session,
                                      String ipAddress,
                                      String userAgent,
                                      String deviceId,
                                      String deviceName) {
        if (session == null) {
            session = resolveSession(user.getId(), deviceId, deviceName, DeviceType.WEB, userAgent, ipAddress);
        }

        UUID orgId = resolveActiveOrganization(user.getId(), user.getDefaultOrganizationId());
        Set<Permission> permissions = permissionResolver.resolve(user.getId(), orgId);

        PrabhixPrincipal principal = new PrabhixPrincipal(
                user.getId(), user.getEmail(), user.effectiveDisplayName(),
                orgId, permissions, session.getId(), user.isPlatformAdmin());
        IssuedToken access = jwtService.issue(principal);

        String rawRefresh = com.prabhix.platform.common.util.Ids.token();
        createRefreshToken(user.getId(), session.getId(), rawRefresh);

        return new TokenResponse(
                access.token(),
                rawRefresh,
                access.expiresInSeconds(),
                orgId,
                permissions.stream().map(Permission::name).collect(Collectors.toUnmodifiableSet()));
    }

    private DeviceSession resolveSession(UUID userId,
                                         String deviceId,
                                         String deviceName,
                                         DeviceType deviceType,
                                         String userAgent,
                                         String ipAddress) {
        if (deviceId != null && !deviceId.isBlank()) {
            Optional<DeviceSession> existing =
                    deviceSessionRepository.findByUserIdAndDeviceIdAndRevokedAtIsNull(userId, deviceId);
            if (existing.isPresent()) {
                DeviceSession session = existing.get();
                session.setLastSeenAt(Instant.now());
                if (deviceName != null && !deviceName.isBlank()) {
                    session.setDeviceName(deviceName);
                }
                if (userAgent != null) {
                    session.setUserAgent(truncate(userAgent, 500));
                }
                if (ipAddress != null) {
                    session.setIpAddress(ipAddress);
                }
                return deviceSessionRepository.save(session);
            }
        }

        DeviceSession session = new DeviceSession();
        session.setUserId(userId);
        session.setDeviceId(deviceId);
        session.setDeviceName(deviceName);
        session.setDeviceType(deviceType != null ? deviceType : DeviceType.WEB);
        session.setUserAgent(truncate(userAgent, 500));
        session.setIpAddress(ipAddress);
        session.setLastSeenAt(Instant.now());
        return deviceSessionRepository.save(session);
    }

    private RefreshToken createRefreshToken(UUID userId, UUID sessionId, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setSessionId(sessionId);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(Instant.now().plus(properties.security().jwt().refreshTokenTtl()));
        return refreshTokenRepository.save(token);
    }

    private UUID resolveActiveOrganization(UUID userId, UUID defaultOrgId) {
        if (defaultOrgId != null) {
            Optional<OrganizationMembership> membership = membershipRepository
                    .findByOrganizationIdAndUserId(defaultOrgId, userId);
            if (membership.isPresent() && membership.get().getStatus() == MembershipStatus.ACTIVE) {
                return defaultOrgId;
            }
        }
        List<OrganizationMembership> active =
                membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        if (active.size() == 1) {
            return active.get(0).getOrganizationId();
        }
        return null;
    }

    private void handleTokenTheft(RefreshToken reused) {
        UUID chainId = reused.getId();
        revokeTokenChain(chainId);
        revokeAllUserSessions(reused.getUserId());
        tokenDenyList.revokeUser(reused.getUserId());
    }

    private void revokeTokenChain(UUID tokenId) {
        refreshTokenRepository.findById(tokenId).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            if (token.getReplacedBy() != null) {
                revokeTokenChain(token.getReplacedBy());
            }
        });
    }

    private void revokeAllUserSessions(UUID userId) {
        Instant now = Instant.now();
        deviceSessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId)
                .forEach(session -> {
                    session.setRevokedAt(now);
                    session.setRevokedReason("token_theft");
                    deviceSessionRepository.save(session);
                    tokenDenyList.revokeSession(session.getId());
                });
    }

    private DeviceType parseDeviceType(String value) {
        if (value == null || value.isBlank()) {
            return DeviceType.WEB;
        }
        return DeviceType.valueOf(value.trim().toUpperCase());
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
