package com.prabhix.platform.org.service;

import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.security.rbac.SystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionSynchronizerTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionResolver permissionResolver;

    private RolePermissionSynchronizer synchronizer;
    private Map<String, Role> stored;

    @BeforeEach
    void setUp() {
        synchronizer = new RolePermissionSynchronizer(roleRepository, permissionResolver);
        stored = new HashMap<>();

        for (SystemRole systemRole : SystemRole.values()) {
            Role role = new Role();
            role.setId(UUID.randomUUID());
            role.setRoleKey(systemRole.name());
            role.setSystem(true);
            role.setPermissionCodes(systemRole.permissions().stream()
                    .map(Permission::name)
                    .collect(Collectors.toCollection(HashSet::new)));
            stored.put(systemRole.name(), role);
        }

        when(roleRepository.findByOrganizationIdIsNullAndRoleKey(anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0, String.class))));
        when(roleRepository.save(any(Role.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void grantsPermissionsMissingFromASeededRole() {
        Role admin = stored.get(SystemRole.ADMIN.name());
        admin.getPermissionCodes().remove(Permission.CHAT_REPLY.name());

        synchronizer.synchronize();

        assertTrue(admin.getPermissionCodes().contains(Permission.CHAT_REPLY.name()));
        verify(permissionResolver).evictAll();
    }

    @Test
    void revokesPermissionsARoleShouldNoLongerHold() {
        Role viewer = stored.get(SystemRole.VIEWER.name());
        viewer.getPermissionCodes().add(Permission.BILLING_MANAGE.name());

        synchronizer.synchronize();

        assertTrue(viewer.getPermissionCodes().stream()
                .noneMatch(code -> code.equals(Permission.BILLING_MANAGE.name())));
    }

    @Test
    void ownerHoldsEveryAssignablePermissionAfterSync() {
        Role owner = stored.get(SystemRole.OWNER.name());
        owner.setPermissionCodes(new HashSet<>(Set.of(Permission.ORG_READ.name())));

        synchronizer.synchronize();

        Set<String> expected = Permission.assignable().stream()
                .map(Permission::name)
                .collect(Collectors.toSet());
        assertEquals(expected, owner.getPermissionCodes());
    }

    @Test
    void leavesCacheAloneWhenNothingDrifted() {
        synchronizer.synchronize();

        verify(roleRepository, never()).save(any(Role.class));
        verify(permissionResolver, never()).evictAll();
    }
}
