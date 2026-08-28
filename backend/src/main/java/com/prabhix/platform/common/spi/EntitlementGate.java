package com.prabhix.platform.common.spi;

import java.util.UUID;

/**
 * Plan-limit checks, declared here so {@code org} and {@code mail} can enforce entitlements
 * without compiling against {@code billing}. The billing module supplies the implementation.
 *
 * <p>These are calls rather than events because they must be able to reject the caller's
 * action: an event cannot stop the work that published it.
 *
 * <p>Every method throws {@code ApiException} when the organization is over its limit and
 * returns silently when it is within it.
 */
public interface EntitlementGate {

    /**
     * @param key          entitlement key on the plan, e.g. {@code mailboxes}
     * @param currentUsage usage before the resource being created is added
     */
    void requireQuota(UUID organizationId, String key, long currentUsage);

    /** Rejects when the plan does not include a gated capability. */
    void requireFeature(UUID organizationId, String key);

    /** Rejects when adding one more member would exceed the subscribed seat count. */
    void requireMemberSeat(UUID organizationId, long currentMemberCount);
}
