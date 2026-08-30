package com.prabhix.platform.org.service;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.security.rbac.SystemRole;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionResolverTest {

    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private PermissionResolver resolver;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        resolver = new PermissionResolver(
                membershipRepository, roleRepository, userRepository, redis, new ObjectMapper());
    }

    @Test
    void ownerReceivesAllAssignablePermissions() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        stubMembership(userId, orgId, roleId);
        stubRole(roleId, SystemRole.OWNER.name(), SystemRole.OWNER.permissions());

        Set<Permission> permissions = resolver.resolve(userId, orgId);

        assertEquals(SystemRole.OWNER.permissions(), permissions);
    }

    @Test
    void agentDoesNotReceiveMailReadAll() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        stubMembership(userId, orgId, roleId);
        stubRole(roleId, SystemRole.AGENT.name(), SystemRole.AGENT.permissions());

        Set<Permission> permissions = resolver.resolve(userId, orgId);

        assertTrue(permissions.contains(Permission.MAIL_READ));
        assertTrue(permissions.stream().noneMatch(p -> p == Permission.MAIL_READ_ALL));
    }

    @Test
    void platformAdminReceivesPlatformAdminPermission() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setPlatformAdmin(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Set<Permission> permissions = resolver.resolve(userId, null);

        assertTrue(permissions.contains(Permission.PLATFORM_ADMIN));
        assertTrue(permissions.contains(Permission.SITE_LEAD_READ));
        assertTrue(permissions.contains(Permission.SITE_APPLICATION_MANAGE));
    }

    private void stubMembership(UUID userId, UUID orgId, UUID roleId) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(userId);
        membership.setOrganizationId(orgId);
        membership.setRoleId(roleId);
        membership.setStatus(MembershipStatus.ACTIVE);
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, userId))
                .thenReturn(Optional.of(membership));
        User user = new User();
        user.setId(userId);
        user.setPlatformAdmin(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private void stubRole(UUID roleId, String roleKey, Set<Permission> permissions) {
        Role role = new Role();
        role.setId(roleId);
        role.setRoleKey(roleKey);
        role.setPermissionCodes(permissions.stream().map(Permission::name).collect(Collectors.toSet()));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
    }
}
