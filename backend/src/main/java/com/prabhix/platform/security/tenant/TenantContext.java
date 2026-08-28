package com.prabhix.platform.security.tenant;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;

import java.util.Optional;
import java.util.UUID;

/**
 * The active organization for the current thread.
 *
 * <p>Set by {@code TenantFilter} at the edge and cleared in a {@code finally} block, because
 * a leaked value on a pooled thread would hand the next request another tenant's data.
 *
 * <p>Uses an {@link InheritableThreadLocal} so work handed to {@code @Async} inherits the
 * tenant. Background workers that legitimately span tenants (the outbox drainer, IMAP
 * fetchers) must call {@link #runAs} per unit of work rather than relying on inheritance.
 */
public final class TenantContext {

    private static final InheritableThreadLocal<UUID> CURRENT = new InheritableThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID organizationId) {
        CURRENT.set(organizationId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    /**
     * @throws ApiException when no organization is active. Tenant-scoped queries fail loudly
     *         rather than silently returning every tenant's rows.
     */
    public static UUID require() {
        UUID organizationId = CURRENT.get();
        if (organizationId == null) {
            throw ApiException.of(ErrorCode.ORGANIZATION_REQUIRED,
                    "Select an organization before performing this action");
        }
        return organizationId;
    }

    /** Runs work under an explicit tenant, restoring whatever was set before. */
    public static void runAs(UUID organizationId, Runnable work) {
        UUID previous = CURRENT.get();
        try {
            CURRENT.set(organizationId);
            TenantFilterBridge.applyToCurrentSession();
            work.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static <T> T callAs(UUID organizationId, java.util.function.Supplier<T> work) {
        UUID previous = CURRENT.get();
        try {
            CURRENT.set(organizationId);
            TenantFilterBridge.applyToCurrentSession();
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
