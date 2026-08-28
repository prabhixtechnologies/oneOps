package com.prabhix.platform.security.apikey;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.domain.ApiKey;
import com.prabhix.platform.org.service.ApiKeyService;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private static final java.time.Duration LAST_USED_THROTTLE = java.time.Duration.ofMinutes(1);

    private final ApiKeyService apiKeyService;
    private final UserRepository userRepository;
    private final PermissionResolver permissionResolver;
    private final ConcurrentHashMap<UUID, Instant> lastUsedWriteAt = new ConcurrentHashMap<>();

    @Transactional
    public PrabhixPrincipal authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || !rawKey.startsWith(ApiKeyService.KEY_PREFIX)) {
            throw ApiException.of(ErrorCode.API_KEY_INVALID, "That API key is not valid");
        }

        ApiKey apiKey = apiKeyService.findActiveByRawKey(rawKey)
                .orElseThrow(() -> ApiException.of(ErrorCode.API_KEY_INVALID, "That API key is not valid"));

        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.of(ErrorCode.API_KEY_EXPIRED, "That API key has expired");
        }

        User creator = userRepository.findById(apiKey.getCreatedByUser())
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.API_KEY_INVALID, "That API key is not valid"));

        recordLastUsed(apiKey);

        Set<Permission> permissions = apiKey.getScopes().stream()
                .map(Permission::parse)
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));

        // Scopes are frozen at creation, so on their own they would let a key keep the authority
        // its creator had on that day. Intersecting with what the creator can do right now means
        // demoting or removing someone also defuses every key they issued.
        Set<Permission> creatorNow = permissionResolver.resolve(creator.getId(), apiKey.getOrganizationId());
        permissions.retainAll(creatorNow);
        if (permissions.isEmpty()) {
            throw ApiException.of(ErrorCode.API_KEY_INVALID,
                    "That API key's owner no longer has access to this organization");
        }

        return new PrabhixPrincipal(
                creator.getId(),
                creator.getEmail(),
                creator.effectiveDisplayName(),
                apiKey.getOrganizationId(),
                permissions,
                null,
                false);
    }

    private void recordLastUsed(ApiKey apiKey) {
        Instant now = Instant.now();
        Instant previous = lastUsedWriteAt.get(apiKey.getId());
        if (previous != null && previous.plus(LAST_USED_THROTTLE).isAfter(now)) {
            return;
        }
        lastUsedWriteAt.put(apiKey.getId(), now);
        apiKeyService.touchLastUsed(apiKey.getId(), now);
    }
}
