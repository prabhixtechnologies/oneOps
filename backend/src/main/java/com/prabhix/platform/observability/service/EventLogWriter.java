package com.prabhix.platform.observability.service;

import com.prabhix.platform.observability.domain.EventLog;
import com.prabhix.platform.observability.event.EventLogRequested;
import com.prabhix.platform.observability.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogWriter {

    private final EventLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEventLogRequested(EventLogRequested event) {
        try {
            EventLog row = new EventLog();
            EventLog.EventLogId id = new EventLog.EventLogId();
            id.setId(UUID.randomUUID());
            id.setOccurredAt(Instant.now());
            row.setId(id);
            row.setOrganizationId(event.organizationId());
            row.setEventCode(event.eventCode().code());
            row.setCategory(event.category().name());
            row.setSeverity(event.severity().name());
            row.setCorrelationId(event.correlationId());
            row.setActorUserId(event.actorUserId());
            row.setActorType(event.actorType());
            row.setActorLabel(event.actorLabel());
            row.setTargetType(event.targetType());
            row.setTargetId(event.targetId());
            row.setPayload(event.payload());
            row.setIpAddress(event.ipAddress());
            row.setUserAgent(event.userAgent());
            row.setSecurityEvent(event.securityEvent());
            row.setContainsPii(event.containsPii());
            repository.save(row);
        } catch (Exception ex) {
            log.error("Failed to persist event log {}: {}", event.eventCode().code(), ex.getMessage(), ex);
        }
    }
}
