package com.prabhix.platform.audit.service;

import com.prabhix.platform.audit.domain.AuditLog;
import com.prabhix.platform.audit.dto.AuditDtos.AuditLogView;
import com.prabhix.platform.audit.repository.AuditLogRepository;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final PrabhixProperties properties;

    // AFTER_COMMIT runs outside the publisher's transaction, so a new one is required.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditRequested(AuditRequested event) {
        try {
            AuditLog entry = new AuditLog();
            AuditLog.AuditLogId id = new AuditLog.AuditLogId();
            id.setId(UUID.randomUUID());
            id.setCreatedAt(Instant.now());
            entry.setId(id);
            entry.setOrganizationId(event.organizationId());
            entry.setActorUserId(event.actorUserId());
            entry.setActorEmail(event.actorEmail());
            entry.setAction(event.action());
            entry.setResourceType(event.resourceType());
            entry.setResourceId(event.resourceId());
            entry.setResourceLabel(event.resourceLabel());
            entry.setChanges(event.changes());
            entry.setMetadata(event.metadata());
            entry.setOutcome(event.success() ? "SUCCESS" : "FAILURE");
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.error("Failed to write audit log for action {}: {}", event.action(), ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<AuditLogView> list(UUID organizationId,
                                         String action,
                                         UUID actorUserId,
                                         String resourceType,
                                         Instant from,
                                         Instant to,
                                         String cursor,
                                         int limit) {
        Cursor decoded = Cursor.decode(cursor);
        Instant cursorCreated = decoded == null ? null : decoded.timestamp();
        UUID cursorId = decoded == null ? null : decoded.id();

        var rows = auditLogRepository.findPage(
                organizationId, action, actorUserId, resourceType, from, to,
                cursorCreated, cursorId, PageRequest.of(0, limit + 1));

        return CursorPage.of(rows, limit, this::toView,
                log -> Cursor.of(log.getId().getCreatedAt(), log.getId().getId()).encode());
    }

    private AuditLogView toView(AuditLog log) {
        return new AuditLogView(
                log.getId().getId(),
                log.getId().getCreatedAt(),
                log.getOrganizationId(),
                log.getActorUserId(),
                log.getActorEmail(),
                log.getActorType(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getResourceLabel(),
                log.getChanges(),
                log.getMetadata(),
                log.getOutcome());
    }
}
