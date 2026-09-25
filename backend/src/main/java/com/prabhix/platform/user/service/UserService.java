package com.prabhix.platform.user.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.domain.User.UserStatus;
import com.prabhix.platform.user.dto.UserDtos.NotificationPrefsRequest;
import com.prabhix.platform.user.dto.UserDtos.UpdateProfileRequest;
import com.prabhix.platform.user.dto.UserDtos.UserProfile;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TokenDenyList tokenDenyList;
    private final FileStorageService fileStorageService;

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
        user.setAvatarUrl(url.orElse("/api/v1/oneops/files?id=" + stored.getId()));
        return toProfile(userRepository.save(user));
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
    public void setDefaultOrganization(UUID userId, UUID organizationId) {
        User user = requireActive(userId);
        user.setDefaultOrganizationId(organizationId);
        userRepository.save(user);
    }

    @Transactional
    public void markEmailVerified(UUID userId) {
        User user = requireActive(userId);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, UUID currentSessionId) {
        tokenDenyList.revokeSession(sessionId);
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
}
