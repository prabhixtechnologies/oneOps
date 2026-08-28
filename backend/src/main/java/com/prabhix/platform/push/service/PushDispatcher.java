package com.prabhix.platform.push.service;

import com.prabhix.platform.push.config.PushProperties;
import com.prabhix.platform.push.domain.PushOutbox;
import com.prabhix.platform.push.domain.PushEnums;
import com.prabhix.platform.push.domain.PushToken;
import com.prabhix.platform.push.repository.PushOutboxRepository;
import com.prabhix.platform.push.util.PushJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushDispatcher {

    private final PushOutboxRepository outboxRepository;
    private final PushProperties properties;

    @Transactional
    public UUID enqueue(PushToken token, String notificationType, Map<String, Object> payload,
                        String dedupeKey) {
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            var existing = outboxRepository.findByDedupeKey(dedupeKey);
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }

        PushOutbox row = new PushOutbox();
        row.setOrganizationId(token.getOrganizationId());
        row.setUserId(token.getUserId());
        row.setPushTokenId(token.getId());
        row.setNotificationType(notificationType);
        row.setPayload(PushJson.toJson(payload));
        row.setDedupeKey(dedupeKey);
        row.setStatus(PushEnums.OutboxStatus.PENDING);
        row.setScheduledAt(Instant.now());
        row.setMaxAttempts(properties.outbox().maxAttempts());
        return outboxRepository.save(row).getId();
    }
}
