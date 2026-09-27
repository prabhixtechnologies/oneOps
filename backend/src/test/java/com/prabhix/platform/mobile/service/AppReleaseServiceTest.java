package com.prabhix.platform.mobile.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mobile.config.MobileProperties;
import com.prabhix.platform.mobile.config.MobileProperties.AppReleasePolicy;
import com.prabhix.platform.mobile.config.MobileProperties.PlatformRelease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppReleaseServiceTest {

    private AppReleaseService service;

    @BeforeEach
    void setUp() {
        service = new AppReleaseService(defaultProperties());
    }

    @Test
    void defaultsDoNotRequireUpdateForTypicalBuild() {
        var response = service.evaluate("oneops", "ANDROID", 42);

        assertThat(response.minNativeBuild()).isEqualTo(1);
        assertThat(response.latestNativeBuild()).isEqualTo(1);
        assertThat(response.updateRequired()).isFalse();
        assertThat(response.forceNativeUpdate()).isFalse();
        assertThat(response.platform()).isEqualTo("ANDROID");
    }

    @Test
    void normalizesAppAndDetectsSoftUpdate() {
        service = new AppReleaseService(propertiesWithAndroid(
                "oneops", platform(1, 10, false, "https://play.example/oneops", "New features")));
        var response = service.evaluate("OneOps", "android", 5);

        assertThat(response.updateRequired()).isTrue();
        assertThat(response.forceNativeUpdate()).isFalse();
        assertThat(response.storeUrl()).isEqualTo("https://play.example/oneops");
        assertThat(response.notes()).isEqualTo("New features");
    }

    @Test
    void forcesWhenBelowMinBuild() {
        service = new AppReleaseService(propertiesWithAndroid(
                "admin", platform(8, 12, false, "", "")));
        var response = service.evaluate("admin", "ANDROID", 7);

        assertThat(response.forceNativeUpdate()).isTrue();
        assertThat(response.updateRequired()).isTrue();
    }

    @Test
    void rejectsUnknownAppAndPlatform() {
        assertThatThrownBy(() -> service.evaluate("mobistack", "ANDROID", 1))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.evaluate("oneops", "WEB", 1))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void normalizeHelpersAllowlist() {
        assertThat(AppReleaseService.normalizeApp("Mailroom")).isEqualTo("mailroom");
        assertThat(AppReleaseService.normalizePlatform("ios")).isEqualTo("IOS");
    }

    private static MobileProperties defaultProperties() {
        return new MobileProperties(300, 120, policy(1, 1), policy(1, 1), policy(1, 1));
    }

    private static MobileProperties propertiesWithAndroid(String app, PlatformRelease android) {
        AppReleasePolicy configured = new AppReleasePolicy(android, platform(1, 1, false, "", ""));
        return switch (app) {
            case "oneops" -> new MobileProperties(300, 120, configured, policy(1, 1), policy(1, 1));
            case "admin" -> new MobileProperties(300, 120, policy(1, 1), configured, policy(1, 1));
            default -> new MobileProperties(300, 120, policy(1, 1), policy(1, 1), configured);
        };
    }

    private static AppReleasePolicy policy(int min, int latest) {
        return new AppReleasePolicy(platform(min, latest, false, "", ""), platform(min, latest, false, "", ""));
    }

    private static PlatformRelease platform(int min, int latest, boolean force, String store, String notes) {
        return new PlatformRelease(min, latest, force, store, notes);
    }
}
