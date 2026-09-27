package com.prabhix.platform.mobile.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Native app upgrade policy served from {@code GET /api/v1/oneops/public/app-release}.
 *
 * <p>Defaults keep {@code minNativeBuild} at 1 so existing store builds are not hard-blocked until
 * operators raise the floor deliberately.
 */
@Validated
@ConfigurationProperties(prefix = "prabhix.mobile")
public record MobileProperties(
        @Min(1) @DefaultValue("300") int appReleaseCacheMaxAgeSeconds,
        @Min(1) @DefaultValue("120") int appReleaseRateLimitPerMinute,
        @Valid @DefaultValue AppReleasePolicy oneops,
        @Valid @DefaultValue AppReleasePolicy admin,
        @Valid @DefaultValue AppReleasePolicy mailroom) {

    public record AppReleasePolicy(
            @Valid @DefaultValue PlatformRelease android,
            @Valid @DefaultValue PlatformRelease ios) {
    }

    public record PlatformRelease(
            @Min(1) @DefaultValue("1") int minNativeBuild,
            @Min(1) @DefaultValue("1") int latestNativeBuild,
            @DefaultValue("false") boolean forceNativeUpdate,
            @DefaultValue("") String storeUrl,
            @DefaultValue("") String notes) {

        public PlatformRelease {
            minNativeBuild = Math.max(1, minNativeBuild);
            latestNativeBuild = Math.max(minNativeBuild, latestNativeBuild);
        }
    }
}
