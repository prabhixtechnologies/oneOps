package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.domain.ApiKey;
import com.prabhix.platform.org.dto.OrgDtos;
import com.prabhix.platform.org.repository.ApiKeyRepository;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    public static final String KEY_PREFIX = "pbx_live_";

    private final ApiKeyRepository apiKeyRepository;
    private final PermissionResolver permissionResolver;

    @Transactional(readOnly = true)
    public PageResponse<OrgDtos.ApiKeyView> list(UUID organizationId) {
        List<OrgDtos.ApiKeyView> keys = apiKeyRepository
                .findByOrganizationIdAndRevokedAtIsNullOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::toView)
                .toList();
        return PageResponse.of(keys);
    }

    @Transactional
    public OrgDtos.CreatedApiKeyView create(UUID organizationId, UUID userId, OrgDtos.CreateApiKeyRequest request) {
        String rawKey = KEY_PREFIX + Ids.token(24);
        ApiKey apiKey = new ApiKey();
        apiKey.setOrganizationId(organizationId);
        apiKey.setName(request.name().trim());
        apiKey.setKeyPrefix(rawKey.substring(0, Math.min(16, rawKey.length())));
        apiKey.setKeyHash(InvitationService.sha256(rawKey));
        apiKey.setCreatedByUser(userId);
        Set<Permission> creatorPermissions = permissionResolver.resolve(userId, organizationId);
        apiKey.setScopes(resolveStoredScopes(request.scopes(), creatorPermissions));
        if (request.expiresAt() != null) {
            apiKey.setExpiresAt(request.expiresAt());
        }
        apiKey = apiKeyRepository.save(apiKey);
        return new OrgDtos.CreatedApiKeyView(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                rawKey,
                apiKey.getLastUsedAt(),
                apiKey.getCreatedAt(),
                apiKey.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Optional<ApiKey> findActiveByRawKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHashAndRevokedAtIsNull(InvitationService.sha256(rawKey));
    }

    @Transactional
    public void touchLastUsed(UUID keyId, Instant usedAt) {
        apiKeyRepository.findById(keyId).ifPresent(apiKey -> {
            apiKey.setLastUsedAt(usedAt);
            apiKeyRepository.save(apiKey);
        });
    }

    @Transactional
    public void revoke(UUID organizationId, UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndOrganizationIdAndRevokedAtIsNull(keyId, organizationId)
                .orElseThrow(() -> ApiException.notFound("API key"));
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
    }

    private List<String> resolveStoredScopes(Set<String> requested, Set<Permission> creatorPermissions) {
        if (requested == null) {
            return creatorPermissions.stream().map(Permission::name).sorted().toList();
        }
        if (requested.isEmpty()) {
            throw ApiException.withFields(ErrorCode.VALIDATION_FAILED,
                    "At least one scope is required",
                    Map.of("scopes", "Provide one or more permission codes, or omit scopes to inherit all"));
        }

        Set<String> unknown = new LinkedHashSet<>();
        Set<String> notHeld = new LinkedHashSet<>();
        Set<Permission> resolved = new LinkedHashSet<>();

        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String code = raw.trim();
            Optional<Permission> permission = Permission.parse(code);
            if (permission.isEmpty()) {
                unknown.add(code);
                continue;
            }
            if (!creatorPermissions.contains(permission.get())) {
                notHeld.add(code);
                continue;
            }
            resolved.add(permission.get());
        }

        if (!unknown.isEmpty()) {
            throw ApiException.withFields(ErrorCode.VALIDATION_FAILED,
                    "One or more scopes are not valid permission codes",
                    Map.of("scopes", "Unknown: " + String.join(", ", unknown)));
        }
        if (!notHeld.isEmpty()) {
            throw ApiException.withFields(ErrorCode.VALIDATION_FAILED,
                    "You cannot grant scopes you do not hold",
                    Map.of("scopes", "Not held: " + String.join(", ", notHeld)));
        }
        if (resolved.isEmpty()) {
            throw ApiException.withFields(ErrorCode.VALIDATION_FAILED,
                    "At least one scope is required",
                    Map.of("scopes", "Provide one or more permission codes, or omit scopes to inherit all"));
        }

        return resolved.stream().map(Permission::name).sorted().collect(Collectors.toCollection(ArrayList::new));
    }

    private OrgDtos.ApiKeyView toView(ApiKey key) {
        return new OrgDtos.ApiKeyView(
                key.getId(),
                key.getName(),
                key.getKeyPrefix(),
                key.getLastUsedAt(),
                key.getCreatedAt(),
                key.getExpiresAt());
    }
}
