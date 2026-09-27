package com.prabhix.platform.mobile.dto;

public final class AppReleaseDtos {

    private AppReleaseDtos() {
    }

    public record AppReleaseResponse(
            String platform,
            int minNativeBuild,
            int latestNativeBuild,
            boolean forceNativeUpdate,
            boolean updateRequired,
            String storeUrl,
            String notes) {
    }
}
