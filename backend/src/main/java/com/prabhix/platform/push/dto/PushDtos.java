package com.prabhix.platform.push.dto;

import com.prabhix.platform.push.domain.PushEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PushDtos {

    private PushDtos() {
    }

    public record RegisterPushTokenRequest(
            @NotBlank @Size(max = 512) String token,
            @NotNull PushEnums.Platform platform,
            @NotBlank @Size(max = 120) String deviceId,
            @Size(max = 160) String deviceName,
            @Size(max = 32) String appVersion) {
    }

    public record RegisterPushTokenResponse(UUID id) {
    }

    public record DeviceView(
            UUID id,
            PushEnums.Platform platform,
            String deviceId,
            String deviceName,
            String appVersion,
            Instant lastSeenAt,
            boolean enabled) {
    }
}
