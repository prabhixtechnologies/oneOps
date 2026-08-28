package com.prabhix.platform.push.outbound;

import com.prabhix.platform.push.config.PushProperties;
import com.prabhix.platform.push.domain.PushEnums;
import com.prabhix.platform.push.domain.PushOutbox;
import com.prabhix.platform.push.domain.PushToken;
import com.prabhix.platform.push.provider.PushProvider;
import com.prabhix.platform.push.provider.PushProviderRouter;
import com.prabhix.platform.push.repository.PushOutboxRepository;
import com.prabhix.platform.push.repository.PushTokenRepository;
import com.prabhix.platform.push.service.PushTokenService;
import com.prabhix.platform.push.util.PushJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushOutboxWorkerTest {

    @Mock private PushOutboxRepository outboxRepository;
    @Mock private PushTokenRepository tokenRepository;
    @Mock private PushProviderRouter providerRouter;
    @Mock private PushTokenService tokenService;
    @Mock private PushProvider provider;

    private PushOutboxWorker worker;

    @BeforeEach
    void setUp() {
        PushProperties properties = new PushProperties(
                "LOGGING",
                new PushProperties.Outbox(true, 10, java.time.Duration.ofSeconds(5), 6),
                new PushProperties.Fcm("", ""),
                new PushProperties.Apns("", "", "", "", "https://api.push.apple.com"));
        worker = new PushOutboxWorker(properties, outboxRepository, tokenRepository,
                providerRouter, tokenService);
    }

    @Test
    void degradesSilentlyWhenProviderUnconfigured() {
        PushToken token = activeToken();
        PushOutbox row = outboxRow(token.getId());
        when(tokenRepository.findById(token.getId())).thenReturn(Optional.of(token));
        when(providerRouter.select(PushEnums.Platform.FCM)).thenReturn(provider);
        when(provider.send(any())).thenReturn(PushProvider.SendResult.ok("noop"));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.processRow(row);

        verify(provider).send(any());
        verify(tokenService, never()).disableTokens(any());
    }

    @Test
    void prunesInvalidTokensFromProvider() {
        PushToken token = activeToken();
        token.setToken("bad-tok");
        PushOutbox row = outboxRow(token.getId());
        when(tokenRepository.findById(token.getId())).thenReturn(Optional.of(token));
        when(providerRouter.select(PushEnums.Platform.FCM)).thenReturn(provider);
        when(provider.send(any())).thenReturn(
                new PushProvider.SendResult(false, null, "invalid", Set.of("bad-tok")));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.processRow(row);

        verify(tokenService).disableTokens(java.util.List.of("bad-tok"));
    }

    private PushToken activeToken() {
        PushToken token = new PushToken();
        token.setId(UUID.randomUUID());
        token.setToken("tok");
        token.setPlatform(PushEnums.Platform.FCM);
        token.setEnabled(true);
        return token;
    }

    private PushOutbox outboxRow(UUID tokenId) {
        PushOutbox row = new PushOutbox();
        row.setId(UUID.randomUUID());
        row.setOrganizationId(UUID.randomUUID());
        row.setUserId(UUID.randomUUID());
        row.setPushTokenId(tokenId);
        row.setNotificationType("chat.message");
        row.setPayload(PushJson.toJson(Map.of("type", "chat.message")));
        row.setStatus(PushEnums.OutboxStatus.CLAIMED);
        row.setMaxAttempts(6);
        return row;
    }
}
