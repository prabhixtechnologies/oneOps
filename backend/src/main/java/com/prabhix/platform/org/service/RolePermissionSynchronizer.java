package com.prabhix.platform.org.service;

import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.security.rbac.SystemRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Reconciles the seeded system roles with {@link SystemRole}, which is the source of truth.
 *
 * <p>Without this, a permission introduced by a migration only reaches the roles that
 * migration happened to name, and the {@code role_permissions} rows drift from the enum
 * the code reasons about. That drift is invisible until someone gets a 403 on a feature
 * their role is supposed to include.
 *
 * <p>Runs on every boot and is idempotent. It only touches roles where
 * {@code organization_id IS NULL}: those are the shared definitions every organization
 * resolves against. Custom roles a customer authored are never modified.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionSynchronizer {

    private final RoleRepository roleRepository;
    private final PermissionResolver permissionResolver;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void synchronize() {
        boolean changed = false;

        for (SystemRole systemRole : SystemRole.values()) {
            Optional<Role> found = roleRepository.findByOrganizationIdIsNullAndRoleKey(systemRole.name());
            if (found.isEmpty()) {
                log.warn("System role {} is missing from the database; the RBAC seed did not run",
                        systemRole.name());
                continue;
            }

            Role role = found.get();
            Set<String> expected = systemRole.permissions().stream()
                    .map(Permission::name)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

            if (role.getPermissionCodes().equals(expected)) {
                continue;
            }

            Set<String> added = new LinkedHashSet<>(expected);
            added.removeAll(role.getPermissionCodes());
            Set<String> removed = new LinkedHashSet<>(role.getPermissionCodes());
            removed.removeAll(expected);

            role.setPermissionCodes(expected);
            roleRepository.save(role);
            changed = true;

            log.info("Reconciled system role {}: granted {}, revoked {}",
                    systemRole.name(),
                    added.isEmpty() ? "nothing" : added,
                    removed.isEmpty() ? "nothing" : removed);
        }

        if (changed) {
            // Cached permission sets were computed from the pre-reconciliation grants, so a
            // signed-in user would keep hitting 403 until the five-minute TTL lapsed.
            permissionResolver.evictAll();
        }
    }
}
