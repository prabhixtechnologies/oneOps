package com.prabhix.platform.common.event;

import java.util.UUID;

/**
 * A new tenant exists. Billing listens to open the trial subscription, which keeps
 * {@code org} free of a dependency on {@code billing} for what is a side effect rather
 * than a precondition.
 *
 * @param organizationId the new tenant
 * @param ownerUserId    the user who created it and holds the Owner role
 */
public record OrganizationCreated(UUID organizationId, UUID ownerUserId) implements PlatformEvent {
}
