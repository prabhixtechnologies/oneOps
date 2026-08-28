package com.prabhix.platform.push.service;

import com.prabhix.platform.push.domain.PushEnums;
import com.prabhix.platform.push.domain.PushToken;
import com.prabhix.platform.push.dto.PushDtos;
import com.prabhix.platform.push.repository.PushTokenRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock private PushTokenRepository tokenRepository;

    private PushTokenService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PushTokenService(tokenRepository);
    }

    @Test
    void upsertDoesNotDuplicateSameToken() {
        PushToken existing = new PushToken();
        existing.setId(UUID.randomUUID());
        existing.setToken("tok-1");
        existing.setUserId(userA);
        existing.setOrganizationId(orgId);

        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(existing));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new PushDtos.RegisterPushTokenRequest(
                "tok-1", PushEnums.Platform.FCM, "device-1", "Phone", "1.0.0");
        service.register(principal(userA), request);

        ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals(userA, captor.getValue().getUserId());
        assertEquals("device-1", captor.getValue().getDeviceId());
    }

    @Test
    void reassignsTokenWhenUserChanges() {
        PushToken existing = new PushToken();
        existing.setId(UUID.randomUUID());
        existing.setToken("shared-tok");
        existing.setUserId(userA);
        existing.setOrganizationId(orgId);

        when(tokenRepository.findByToken("shared-tok")).thenReturn(Optional.of(existing));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(principal(userB), new PushDtos.RegisterPushTokenRequest(
                "shared-tok", PushEnums.Platform.APNS, "device-1", "iPad", "2.0"));

        ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals(userB, captor.getValue().getUserId());
    }

    @Test
    void disableTokensMarksDeleted() {
        PushToken token = new PushToken();
        token.setToken("dead-tok");
        when(tokenRepository.findByToken("dead-tok")).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.disableTokens(java.util.List.of("dead-tok"));

        ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
        verify(tokenRepository).save(captor.capture());
        assertFalse(captor.getValue().isEnabled());
    }

    private PrabhixPrincipal principal(UUID userId) {
        return new PrabhixPrincipal(userId, "u@example.com", "User", orgId,
                Set.of(Permission.CHAT_READ), UUID.randomUUID(), false);
    }
}
