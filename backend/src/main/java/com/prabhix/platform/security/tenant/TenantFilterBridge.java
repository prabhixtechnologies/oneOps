package com.prabhix.platform.security.tenant;

/**
 * Static hook so {@link TenantContext#runAs} can re-apply the Hibernate filter on an already-open
 * session without pulling Spring into {@link TenantContext}.
 */
public final class TenantFilterBridge {

    private static Runnable applier = () -> {};

    private TenantFilterBridge() {
    }

    public static void setApplier(Runnable applier) {
        TenantFilterBridge.applier = applier != null ? applier : () -> {};
    }

    public static void applyToCurrentSession() {
        applier.run();
    }
}
