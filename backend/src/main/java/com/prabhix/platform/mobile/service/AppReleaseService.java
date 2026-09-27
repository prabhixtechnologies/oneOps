package com.prabhix.platform.mobile.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mobile.config.MobileProperties;
import com.prabhix.platform.mobile.config.MobileProperties.AppReleasePolicy;
import com.prabhix.platform.mobile.config.MobileProperties.PlatformRelease;
import com.prabhix.platform.mobile.dto.AppReleaseDtos.AppReleaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppReleaseService {

    private static final Set<String> APPS = Set.of("oneops", "admin", "mailroom");
    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS");

    private final MobileProperties properties;

    public AppReleaseResponse evaluate(String appRaw, String platformRaw, int clientBuild) {
        String app = normalizeApp(appRaw);
        String platform = normalizePlatform(platformRaw);
        int build = Math.max(0, clientBuild);

        PlatformRelease config = platformConfig(app, platform);
        boolean belowMin = build > 0 && build < config.minNativeBuild();
        boolean belowLatest = build > 0 && build < config.latestNativeBuild();
        boolean force = config.forceNativeUpdate() || belowMin;
        boolean updateRequired = force || belowLatest;

        String storeUrl = blankToNull(config.storeUrl());
        String notes = blankToNull(config.notes());

        return new AppReleaseResponse(
                platform,
                config.minNativeBuild(),
                config.latestNativeBuild(),
                force,
                updateRequired,
                storeUrl,
                notes);
    }

    public int cacheMaxAgeSeconds() {
        return properties.appReleaseCacheMaxAgeSeconds();
    }

    private PlatformRelease platformConfig(String app, String platform) {
        AppReleasePolicy policy = switch (app) {
            case "oneops" -> properties.oneops();
            case "admin" -> properties.admin();
            case "mailroom" -> properties.mailroom();
            default -> throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "Unknown app");
        };
        return "IOS".equals(platform) ? policy.ios() : policy.android();
    }

    static String normalizeApp(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "app is required");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!APPS.contains(normalized)) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "app must be oneops, admin, or mailroom");
        }
        return normalized;
    }

    static String normalizePlatform(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "platform is required");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!PLATFORMS.contains(normalized)) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "platform must be ANDROID or IOS");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
