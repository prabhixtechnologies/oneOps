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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationServiceTest {

    @Mock private ApiKeyService apiKeyService;
    @Mock private UserRepository userRepository;
    @Mock private PermissionResolver permissionResolver;

    private ApiKeyAuthenticationService service;

    private final UUID orgA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID orgB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final String rawKey = ApiKeyService.KEY_PREFIX + "abc123def456";

    @BeforeEach
    void setUp() {
        service = new ApiKeyAuthenticationService(apiKeyService, userRepository, permissionResolver);
    }

    private void creatorStillHas(UUID userId, Permission... permissions) {
        when(permissionResolver.resolve(userId, orgA))
                .thenReturn(java.util.EnumSet.copyOf(List.of(permissions)));
    }

    @Test
    void authenticatesValidKey() {
        ApiKey apiKey = activeKey(orgA);
        apiKey.setScopes(List.of(Permission.ORG_READ.name()));
        User creator = creator(apiKey.getCreatedByUser());

        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.of(apiKey));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        creatorStillHas(creator.getId(), Permission.ORG_READ);

        PrabhixPrincipal principal = service.authenticate(rawKey);

        assertEquals(orgA, principal.organizationId());
        assertTrue(principal.has(Permission.ORG_READ));
        verify(apiKeyService).touchLastUsed(eq(apiKey.getId()), any());
    }

    @Test
    void honorsNarrowedScopesStoredOnKey() {
        ApiKey apiKey = activeKey(orgA);
        apiKey.setScopes(List.of(Permission.MAIL_READ.name()));
        User creator = creator(apiKey.getCreatedByUser());

        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.of(apiKey));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        creatorStillHas(creator.getId(), Permission.MAIL_READ, Permission.MAIL_SEND);

        PrabhixPrincipal principal = service.authenticate(rawKey);

        assertTrue(principal.has(Permission.MAIL_READ));
        org.junit.jupiter.api.Assertions.assertFalse(principal.has(Permission.MAIL_SEND),
                "a narrowed key must not gain permissions beyond its stored scopes");
    }

    @Test
    void capsScopesAtWhatTheCreatorCanStillDo() {
        ApiKey apiKey = activeKey(orgA);
        apiKey.setScopes(List.of(Permission.ORG_READ.name(), Permission.ORG_DELETE.name()));
        User creator = creator(apiKey.getCreatedByUser());

        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.of(apiKey));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        creatorStillHas(creator.getId(), Permission.ORG_READ);

        PrabhixPrincipal principal = service.authenticate(rawKey);

        assertTrue(principal.has(Permission.ORG_READ));
        org.junit.jupiter.api.Assertions.assertFalse(principal.has(Permission.ORG_DELETE),
                "a demoted creator's key must lose the permission they lost");
    }

    @Test
    void rejectsKeyWhoseCreatorLostAllAccess() {
        ApiKey apiKey = activeKey(orgA);
        apiKey.setScopes(List.of(Permission.ORG_READ.name()));
        User creator = creator(apiKey.getCreatedByUser());

        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.of(apiKey));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        when(permissionResolver.resolve(creator.getId(), orgA))
                .thenReturn(java.util.EnumSet.noneOf(Permission.class));

        ApiException ex = assertThrows(ApiException.class, () -> service.authenticate(rawKey));
        assertEquals(ErrorCode.API_KEY_INVALID, ex.getCode());
    }

    @Test
    void rejectsUnknownKey() {
        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.authenticate(rawKey));
        assertEquals(ErrorCode.API_KEY_INVALID, ex.getCode());
    }

    @Test
    void rejectsExpiredKey() {
        ApiKey apiKey = activeKey(orgA);
        apiKey.setExpiresAt(Instant.now().minusSeconds(60));
        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.of(apiKey));

        ApiException ex = assertThrows(ApiException.class, () -> service.authenticate(rawKey));
        assertEquals(ErrorCode.API_KEY_EXPIRED, ex.getCode());
    }

    @Test
    void revokedKeyIsNotReturnedByRepository() {
        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.authenticate(rawKey));
        assertEquals(ErrorCode.API_KEY_INVALID, ex.getCode());
    }

    @Test
    void principalCarriesKeyOrganization() {
        ApiKey apiKey = activeKey(orgA);
        apiKey.setScopes(List.of(Permission.ORG_READ.name()));
        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.of(apiKey));
        when(userRepository.findById(apiKey.getCreatedByUser()))
                .thenReturn(Optional.of(creator(apiKey.getCreatedByUser())));
        creatorStillHas(apiKey.getCreatedByUser(), Permission.ORG_READ);

        PrabhixPrincipal principal = service.authenticate(rawKey);
        assertEquals(orgA, principal.organizationId());
        org.junit.jupiter.api.Assertions.assertNotEquals(orgB, principal.organizationId());
    }

    @Test
    void throttlesLastUsedWrites() {
        ApiKey apiKey = activeKey(orgA);
        apiKey.setScopes(List.of(Permission.ORG_READ.name()));
        when(apiKeyService.findActiveByRawKey(rawKey)).thenReturn(Optional.of(apiKey));
        when(userRepository.findById(apiKey.getCreatedByUser()))
                .thenReturn(Optional.of(creator(apiKey.getCreatedByUser())));
        creatorStillHas(apiKey.getCreatedByUser(), Permission.ORG_READ);

        service.authenticate(rawKey);
        service.authenticate(rawKey);

        verify(apiKeyService, times(1)).touchLastUsed(eq(apiKey.getId()), any());
    }

    private ApiKey activeKey(UUID organizationId) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID());
        apiKey.setOrganizationId(organizationId);
        apiKey.setCreatedByUser(UUID.randomUUID());
        apiKey.setName("CI key");
        apiKey.setKeyPrefix("pbx_live_abc");
        return apiKey;
    }

    private User creator(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setFullName("Creator");
        return user;
    }
}
