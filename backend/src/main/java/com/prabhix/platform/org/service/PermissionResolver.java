package com.prabhix.platform.org.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves effective permissions for a user within an organization.
 * Results are cached in Redis with a short TTL; callers must evict on role or membership changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionResolver {

    private static final String CACHE_PREFIX = "pbx:perms:";
    private static final String USER_KEYS_PREFIX = "pbx:perms:keys:user:";
    private static final String ORG_KEYS_PREFIX = "pbx:perms:keys:org:";
    private static final String ALL_KEYS = "pbx:perms:keys:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Set<Permission> resolve(UUID userId, UUID organizationId) {
        if (organizationId == null) {
            return platformOnly(userId);
        }

        String cacheKey = CACHE_PREFIX + userId + ":" + organizationId;
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                List<String> codes = objectMapper.readValue(cached, new TypeReference<>() {
                });
                return codes.stream()
                        .map(Permission::parse)
                        .flatMap(java.util.Optional::stream)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
            }
        } catch (Exception ex) {
            log.warn("Permission cache read failed, falling back to database: {}", ex.getMessage());
        }

        Set<Permission> permissions = loadFromDatabase(userId, organizationId);
        cachePermissions(userId, organizationId, cacheKey, permissions);
        return permissions;
    }

    public void evict(UUID userId, UUID organizationId) {
        try {
            String cacheKey = CACHE_PREFIX + userId + ":" + organizationId;
            redis.delete(cacheKey);
            redis.opsForSet().remove(USER_KEYS_PREFIX + userId, cacheKey);
            redis.opsForSet().remove(ORG_KEYS_PREFIX + organizationId, cacheKey);
            redis.opsForSet().remove(ALL_KEYS, cacheKey);
        } catch (RuntimeException ex) {
            log.warn("Permission cache eviction failed: {}", ex.getMessage());
        }
    }

    public void evictUser(UUID userId) {
        try {
            deleteTrackedKeys(USER_KEYS_PREFIX + userId);
        } catch (RuntimeException ex) {
            log.warn("Permission cache user eviction failed: {}", ex.getMessage());
        }
    }

    public void evictOrganization(UUID organizationId) {
        try {
            deleteTrackedKeys(ORG_KEYS_PREFIX + organizationId);
        } catch (RuntimeException ex) {
            log.warn("Permission cache org eviction failed: {}", ex.getMessage());
        }
    }

    /**
     * Drops every cached permission set. Reserved for changes to the shared system roles,
     * which affect users across all organizations at once. Enumerates a tracked index
     * rather than scanning, so it stays safe against a large keyspace.
     */
    public void evictAll() {
        try {
            deleteTrackedKeys(ALL_KEYS);
        } catch (RuntimeException ex) {
            log.warn("Permission cache global eviction failed: {}", ex.getMessage());
        }
    }

    private void deleteTrackedKeys(String indexKey) {
        Set<String> keys = redis.opsForSet().members(indexKey);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.delete(indexKey);
    }

    private Set<Permission> loadFromDatabase(UUID userId, UUID organizationId) {
        Set<Permission> permissions = new HashSet<>(platformOnly(userId));

        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElse(null);
        if (membership == null || membership.getStatus() != MembershipStatus.ACTIVE) {
            return permissions;
        }

        Role role = roleRepository.findById(membership.getRoleId())
                .orElseThrow(() -> ApiException.of(ErrorCode.INTERNAL_ERROR, "Member role is missing"));

        role.getPermissionCodes().stream()
                .map(Permission::parse)
                .flatMap(java.util.Optional::stream)
                .forEach(permissions::add);

        return Set.copyOf(permissions);
    }

    private Set<Permission> platformOnly(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isPlatformAdmin()) {
            return EnumSet.of(
                    Permission.PLATFORM_ADMIN,
                    Permission.SITE_LEAD_READ,
                    Permission.SITE_LEAD_MANAGE,
                    Permission.SITE_SUBSCRIBER_READ,
                    Permission.SITE_APPLICATION_READ,
                    Permission.SITE_APPLICATION_MANAGE);
        }
        return Set.of();
    }

    private void cachePermissions(UUID userId, UUID organizationId, String cacheKey,
                                  Set<Permission> permissions) {
        try {
            List<String> codes = permissions.stream().map(Permission::name).sorted().toList();
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(codes), CACHE_TTL);
            redis.opsForSet().add(USER_KEYS_PREFIX + userId, cacheKey);
            redis.opsForSet().add(ORG_KEYS_PREFIX + organizationId, cacheKey);
            redis.opsForSet().add(ALL_KEYS, cacheKey);
        } catch (Exception ex) {
            log.warn("Permission cache write failed: {}", ex.getMessage());
        }
    }
}
