package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.domain.ApiKey;
import com.prabhix.platform.org.dto.OrgDtos;
import com.prabhix.platform.org.repository.ApiKeyRepository;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private PermissionResolver permissionResolver;

    private ApiKeyService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyRepository, permissionResolver);
    }

    @Test
    void inheritsAllCreatorPermissionsWhenScopesOmitted() {
        when(permissionResolver.resolve(userId, orgId))
                .thenReturn(EnumSet.of(Permission.ORG_READ, Permission.MAIL_READ));
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(orgId, userId, new OrgDtos.CreateApiKeyRequest("CI", null, null));

        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(saved.capture());
        assertEquals(List.of(Permission.MAIL_READ.name(), Permission.ORG_READ.name()), saved.getValue().getScopes());
    }

    @Test
    void storesRequestedSubsetCreatorHolds() {
        when(permissionResolver.resolve(userId, orgId))
                .thenReturn(EnumSet.of(Permission.ORG_READ, Permission.MAIL_READ, Permission.MAIL_SEND));
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(orgId, userId,
                new OrgDtos.CreateApiKeyRequest("narrow", null, Set.of("MAIL_READ", "MAIL_SEND")));

        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(saved.capture());
        assertEquals(List.of(Permission.MAIL_READ.name(), Permission.MAIL_SEND.name()), saved.getValue().getScopes());
    }

    @Test
    void rejectsScopeCreatorDoesNotHold() {
        when(permissionResolver.resolve(userId, orgId))
                .thenReturn(EnumSet.of(Permission.ORG_READ));

        ApiException ex = assertThrows(ApiException.class, () -> service.create(orgId, userId,
                new OrgDtos.CreateApiKeyRequest("too much", null, Set.of("ORG_DELETE"))));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
        assertTrue(ex.getFieldErrors().get("scopes").contains("ORG_DELETE"));
    }

    @Test
    void rejectsUnknownScopeCode() {
        when(permissionResolver.resolve(userId, orgId))
                .thenReturn(EnumSet.of(Permission.ORG_READ));

        ApiException ex = assertThrows(ApiException.class, () -> service.create(orgId, userId,
                new OrgDtos.CreateApiKeyRequest("bad code", null, Set.of("NOT_A_PERMISSION"))));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
        assertTrue(ex.getFieldErrors().get("scopes").contains("NOT_A_PERMISSION"));
    }

    @Test
    void rejectsEmptyScopeSet() {
        when(permissionResolver.resolve(userId, orgId))
                .thenReturn(EnumSet.of(Permission.ORG_READ));

        ApiException ex = assertThrows(ApiException.class, () -> service.create(orgId, userId,
                new OrgDtos.CreateApiKeyRequest("empty", null, Set.of())));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }
}
