package com.prabhix.platform.push.outbound;

import com.prabhix.platform.mail.util.OutboxBackoff;
import com.prabhix.platform.push.config.PushProperties;
import com.prabhix.platform.push.domain.PushOutbox;
import com.prabhix.platform.push.domain.PushEnums;
import com.prabhix.platform.push.domain.PushToken;
import com.prabhix.platform.push.provider.PushProvider;
import com.prabhix.platform.push.provider.PushProviderRouter;
import com.prabhix.platform.push.repository.PushOutboxRepository;
import com.prabhix.platform.push.repository.PushTokenRepository;
import com.prabhix.platform.push.service.PushTokenService;
import com.prabhix.platform.push.util.PushJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushOutboxWorker {

    private static final Duration STALE_CLAIM = Duration.ofMinutes(10);
    private final String instanceId = "push-" + UUID.randomUUID().toString().substring(0, 8);

    private final PushProperties properties;
    private final PushOutboxRepository outboxRepository;
    private final PushTokenRepository tokenRepository;
    private final PushProviderRouter providerRouter;
    private final PushTokenService tokenService;

    @Scheduled(fixedDelayString = "${prabhix.push.outbox.poll-interval:PT5S}")
    @Transactional
    public void drain() {
        if (!properties.outbox().enabled()) {
            return;
        }
        outboxRepository.releaseStuck(Instant.now().minus(STALE_CLAIM));

        int batchSize = properties.outbox().batchSize();
        Instant now = Instant.now();
        var pending = outboxRepository.claimPending(now, batchSize);
        var retryable = outboxRepository.claimRetryable(now, batchSize);
        pending.addAll(retryable);

        for (PushOutbox row : pending) {
            row.setStatus(PushEnums.OutboxStatus.CLAIMED);
            row.setClaimedAt(now);
            row.setClaimedBy(instanceId);
            outboxRepository.save(row);
            processRow(row);
        }
    }

    void processRow(PushOutbox row) {
        PushToken token = tokenRepository.findById(row.getPushTokenId()).orElse(null);
        if (token == null || token.getDeletedAt() != null || !token.isEnabled()) {
            row.setStatus(PushEnums.OutboxStatus.DEAD);
            row.setLastError("Push token no longer active");
            outboxRepository.save(row);
            return;
        }

        try {
            row.setStatus(PushEnums.OutboxStatus.SENDING);
            outboxRepository.save(row);

            PushProvider provider = providerRouter.select(token.getPlatform());
            var payload = PushJson.parseMap(row.getPayload());
            var result = provider.send(new PushProvider.SendRequest(
                    row.getOrganizationId(),
                    row.getUserId(),
                    token.getPlatform(),
                    token.getToken(),
                    row.getNotificationType(),
                    payload));

            if (result.success()) {
                row.setStatus(PushEnums.OutboxStatus.SENT);
                row.setSentAt(Instant.now());
                row.setProviderUsed(provider.providerId());
                row.setProviderMessageId(result.providerMessageId());
            } else {
                if (result.invalidTokens() != null && !result.invalidTokens().isEmpty()) {
                    tokenService.disableTokens(result.invalidTokens().stream().toList());
                }
                handleFailure(row, provider.providerId(), result.error());
            }
        } catch (Exception ex) {
            handleFailure(row, "UNKNOWN", ex.getMessage());
        }
        outboxRepository.save(row);
    }

    void handleFailure(PushOutbox row, String providerId, String error) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastError(error);

        if (row.getAttempts() >= row.getMaxAttempts()) {
            row.setStatus(PushEnums.OutboxStatus.DEAD);
        } else {
            row.setStatus(PushEnums.OutboxStatus.FAILED);
            row.setNextAttemptAt(Instant.now().plus(OutboxBackoff.nextDelay(row.getAttempts())));
        }
        log.debug("Push outbox row {} failed via {}: {}", row.getId(), providerId, error);
    }
}
