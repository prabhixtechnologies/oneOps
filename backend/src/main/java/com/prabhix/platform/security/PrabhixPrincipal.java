package com.prabhix.platform.security;

import com.prabhix.platform.security.rbac.Permission;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The authenticated caller, rebuilt from the JWT on every request.
 *
 * <p>Permissions are resolved when the token is issued rather than per request. At 100k users
 * a permission lookup per call would be the busiest query in the system; embedding them
 * bounds staleness to the 15-minute access-token TTL instead, with a Redis deny-list for
 * revocations that must take effect immediately.
 *
 * @param userId         the authenticated user
 * @param email          login identity, useful in logs and audit entries
 * @param displayName    for rendering "assigned by" style text without another lookup
 * @param organizationId active organization, or {@code null} before one is selected
 * @param permissions    effective permissions within the active organization
 * @param sessionId      device session, so a single device can be revoked
 * @param platformAdmin  true only for Prabhix staff tokens
 */
public record PrabhixPrincipal(
        UUID userId,
        String email,
        String displayName,
        UUID organizationId,
        Set<Permission> permissions,
        UUID sessionId,
        boolean platformAdmin) {

    public PrabhixPrincipal {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return permissions.stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean hasOrganization() {
        return organizationId != null;
    }

    /**
     * @throws IllegalStateException when called on a token that has no organization selected.
     *         Callers reachable without an organization must check {@link #hasOrganization()}.
     */
    public UUID requireOrganizationId() {
        if (organizationId == null) {
            throw new IllegalStateException("No organization selected on this token");
        }
        return organizationId;
    }
}
