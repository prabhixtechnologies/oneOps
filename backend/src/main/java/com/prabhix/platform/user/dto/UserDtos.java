package com.prabhix.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserProfile(
            UUID id,
            String email,
            boolean emailVerified,
            String fullName,
            String displayName,
            String avatarUrl,
            String jobTitle,
            String timezone,
            String locale,
            String status,
            boolean platformAdmin,
            UUID defaultOrganizationId,
            Map<String, Object> notificationPrefs,
            Instant createdAt) {
    }

    public record UpdateProfileRequest(
            @Size(max = 160) String fullName,
            @Size(max = 80) String displayName,
            @Size(max = 120) String jobTitle,
            @Size(max = 64) String timezone,
            @Size(max = 16) String locale) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    public record NotificationPrefsRequest(
            Map<String, Object> preferences) {
    }

    public record DeviceSessionView(
            UUID id,
            String deviceName,
            String deviceType,
            String ipAddress,
            Instant lastSeenAt,
            Instant createdAt,
            boolean current) {
    }
}
