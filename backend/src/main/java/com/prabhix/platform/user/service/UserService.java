package com.prabhix.platform.user.service;

import com.prabhix.platform.auth.domain.DeviceSession;
import com.prabhix.platform.auth.service.DeviceSessionService;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.domain.User.UserStatus;
import com.prabhix.platform.user.dto.UserDtos.ChangePasswordRequest;
import com.prabhix.platform.user.dto.UserDtos.DeviceSessionView;
import com.prabhix.platform.user.dto.UserDtos.NotificationPrefsRequest;
import com.prabhix.platform.user.dto.UserDtos.UpdateProfileRequest;
import com.prabhix.platform.user.dto.UserDtos.UserProfile;
import com.prabhix.platform.user.repository.UserRepository;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final java.time.Duration LOCK_DURATION = java.time.Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final DeviceSessionService deviceSessionService;
    private final PasswordEncoder passwordEncoder;
    private final TokenDenyList tokenDenyList;
    private final FileStorageService fileStorageService;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public User requireActive(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        if (user.isDeleted()) {
            throw ApiException.notFound("User");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isDeleted());
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return toProfile(requireActive(userId));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireActive(userId);
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
        }
        if (request.jobTitle() != null) {
            user.setJobTitle(request.jobTitle().isBlank() ? null : request.jobTitle().trim());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(request.timezone().trim());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            user.setLocale(request.locale().trim());
        }
        return toProfile(userRepository.save(user));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = requireActive(userId);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "The current password is not correct");
        }
        validatePasswordStrength(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        tokenDenyList.revokeUser(userId);
        revokeAllSessions(userId, "password_changed");
        events.publishEvent(AuditRequested.of(null, userId, "auth.password.changed", "user", userId));
    }

    @Transactional
    public UserProfile updateNotificationPrefs(UUID userId, NotificationPrefsRequest request) {
        User user = requireActive(userId);
        Map<String, Object> prefs = request.preferences() == null ? Map.of() : request.preferences();
        user.setNotificationPrefs(prefs);
        return toProfile(userRepository.save(user));
    }

    @Transactional
    public UserProfile updateAvatar(UUID userId, byte[] content, String filename, String contentType) {
        User user = requireActive(userId);
        StoredFile stored = fileStorageService.storeAvatar(content, filename, contentType, userId);
        Optional<String> url = fileStorageService.signedUrl(
                FileStorageService.PLATFORM_FILES_ORGANIZATION_ID, stored.getId());
        user.setAvatarUrl(url.orElse("/api/v1/files/" + stored.getId()));
        return toProfile(userRepository.save(user));
    }

    @Transactional
    public User createUser(String email, String password, String fullName) {
        String normalised = email.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(normalised).isPresent()) {
            throw ApiException.of(ErrorCode.ALREADY_EXISTS, "An account with that email already exists");
        }
        validatePasswordStrength(password);
        User user = new User();
        user.setEmail(normalised);
        user.setFullName(fullName.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangedAt(Instant.now());
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Transactional
    public User createPasswordlessUser(String email, String fullName) {
        String normalised = email.trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmailIgnoreCase(normalised);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isDeleted()) {
                throw ApiException.notFound("User");
            }
            return user;
        }
        User user = new User();
        user.setEmail(normalised);
        user.setFullName(fullName.trim());
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Transactional
    public void recordLoginFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            user.setStatus(UserStatus.LOCKED);
        }
        userRepository.save(user);
    }

    @Transactional
    public void resetLoginFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public void setDefaultOrganization(UUID userId, UUID organizationId) {
        User user = requireActive(userId);
        user.setDefaultOrganizationId(organizationId);
        userRepository.save(user);
    }

    @Transactional
    public void setPassword(UUID userId, String newPassword) {
        validatePasswordStrength(newPassword);
        User user = requireActive(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        tokenDenyList.revokeUser(userId);
        revokeAllSessions(userId, "password_reset");
    }

    @Transactional
    public void markEmailVerified(UUID userId) {
        User user = requireActive(userId);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<DeviceSessionView> listSessions(UUID userId, UUID currentSessionId) {
        return deviceSessionService.findActiveByUserId(userId)
                .stream()
                .map(session -> toSessionView(session, currentSessionId))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, UUID currentSessionId) {
        DeviceSession session = deviceSessionService.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session"));
        if (!session.getUserId().equals(userId)) {
            throw ApiException.forbidden("That session does not belong to you");
        }
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            session.setRevokedReason("user_revoked");
            deviceSessionService.save(session);
        }
        tokenDenyList.revokeSession(sessionId);
    }

    private void revokeAllSessions(UUID userId, String reason) {
        Instant now = Instant.now();
        deviceSessionService.findActiveByUserId(userId)
                .forEach(session -> {
                    session.setRevokedAt(now);
                    session.setRevokedReason(reason);
                    deviceSessionService.save(session);
                    tokenDenyList.revokeSession(session.getId());
                });
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < properties.security().password().minLength()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "Password must be at least " + properties.security().password().minLength() + " characters");
        }
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getEmailVerifiedAt() != null,
                user.getFullName(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getJobTitle(),
                user.getTimezone(),
                user.getLocale(),
                user.getStatus().name(),
                user.isPlatformAdmin(),
                user.getDefaultOrganizationId(),
                user.getNotificationPrefs(),
                user.getCreatedAt());
    }

    private DeviceSessionView toSessionView(DeviceSession session, UUID currentSessionId) {
        return new DeviceSessionView(
                session.getId(),
                session.getDeviceName(),
                session.getDeviceType().name(),
                session.getIpAddress(),
                session.getLastSeenAt(),
                session.getCreatedAt(),
                session.getId().equals(currentSessionId));
    }
}
