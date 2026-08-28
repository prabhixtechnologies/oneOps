package com.prabhix.platform.observability.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.domain.EventLog;
import com.prabhix.platform.observability.dto.EventLogDtos.EventCodeCount;
import com.prabhix.platform.observability.dto.EventLogDtos.ErrorBucket;
import com.prabhix.platform.observability.dto.EventLogDtos.EventLogStats;
import com.prabhix.platform.observability.dto.EventLogDtos.EventLogView;
import com.prabhix.platform.observability.dto.EventLogDtos.TraceEntry;
import com.prabhix.platform.observability.dto.EventLogDtos.TraceView;
import com.prabhix.platform.observability.repository.EventLogRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventLogQueryService {

    private final EventLogRepository eventLogRepository;
    private final EntityManager entityManager;
    private final PrabhixProperties properties;

    @Transactional(readOnly = true)
    public CursorPage<EventLogView> search(UUID organizationId,
                                           boolean platformAdmin,
                                           UUID requestedOrgId,
                                           String severity,
                                           String category,
                                           String eventCode,
                                           String correlationId,
                                           UUID actorUserId,
                                           String targetType,
                                           UUID targetId,
                                           Instant from,
                                           Instant to,
                                           String search,
                                           String cursor,
                                           int limit) {
        UUID effectiveOrg = resolveOrgScope(organizationId, platformAdmin, requestedOrgId);
        Cursor decoded = Cursor.decode(cursor);
        Instant cursorAt = decoded == null ? null : decoded.timestamp();
        UUID cursorId = decoded == null ? null : decoded.id();

        List<EventLog> rows;
        if (search != null && !search.isBlank()) {
            rows = eventLogRepository.searchPage(
                    effectiveOrg, severity, category, eventCode, correlationId,
                    actorUserId, targetType, targetId, from, to, search.trim(),
                    cursorAt, cursorId, limit + 1);
        } else {
            rows = eventLogRepository.findPage(
                    effectiveOrg, severity, category, eventCode, correlationId,
                    actorUserId, targetType, targetId, from, to,
                    cursorAt, cursorId,
                    PageRequest.of(0, limit + 1));
        }

        return CursorPage.of(rows, limit, this::toView,
                log -> Cursor.of(log.getId().getOccurredAt(), log.getId().getId()).encode());
    }

    @Transactional(readOnly = true)
    public EventLogView getById(UUID organizationId, boolean platformAdmin, UUID requestedOrgId, UUID id) {
        UUID effectiveOrg = resolveOrgScope(organizationId, platformAdmin, requestedOrgId);
        EventLog row = eventLogRepository.findByOrgAndId(effectiveOrg, id)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "Event log entry not found"));
        return toView(row);
    }

    @Transactional(readOnly = true)
    public TraceView trace(UUID organizationId,
                           boolean platformAdmin,
                           UUID requestedOrgId,
                           String correlationId) {
        UUID effectiveOrg = resolveOrgScope(organizationId, platformAdmin, requestedOrgId);

        List<TraceEntry> entries = new ArrayList<>();
        for (EventLog event : eventLogRepository.findByCorrelation(effectiveOrg, correlationId)) {
            entries.add(new TraceEntry(
                    "event_log",
                    event.getId().getOccurredAt(),
                    event.getEventCode(),
                    event.getSeverity(),
                    event.getActorUserId(),
                    event.getActorLabel(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getPayload()));
        }

        for (Object[] row : findAuditByCorrelation(effectiveOrg, correlationId)) {
            entries.add(new TraceEntry(
                    "audit_log",
                    toInstant(row[0]),
                    (String) row[1],
                    (String) row[2],
                    row[3] == null ? null : (UUID) row[3],
                    (String) row[4],
                    (String) row[5],
                    row[6] == null ? null : (UUID) row[6],
                    row[7] == null ? java.util.Map.of() : java.util.Map.of("metadata", row[7])));
        }

        entries.sort(Comparator.comparing(TraceEntry::timestamp));
        return new TraceView(correlationId, List.copyOf(entries));
    }

    @Transactional(readOnly = true)
    public EventLogStats stats(UUID organizationId,
                               boolean platformAdmin,
                               UUID requestedOrgId,
                               Instant from,
                               Instant to) {
        UUID effectiveOrg = resolveOrgScope(organizationId, platformAdmin, requestedOrgId);
        Instant rangeFrom = from == null ? Instant.now().minusSeconds(86_400) : from;
        Instant rangeTo = to == null ? Instant.now() : to;

        List<ErrorBucket> errors = eventLogRepository.countErrorsByHour(effectiveOrg, rangeFrom, rangeTo)
                .stream()
                .map(row -> new ErrorBucket(toInstant(row[0]), ((Number) row[1]).longValue()))
                .toList();

        List<EventCodeCount> top = eventLogRepository.topEventCodes(effectiveOrg, rangeFrom, rangeTo, 10)
                .stream()
                .map(row -> new EventCodeCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        return new EventLogStats(errors, top);
    }

    /**
     * Native queries hand back whichever temporal type the JDBC driver picked for a
     * {@code timestamptz}, and that varies between a plain column read and an expression
     * like {@code date_trunc(...)}. Blind-casting to one of them turns a chart into a 500,
     * so accept the whole family instead.
     */
    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof java.time.ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (value instanceof java.time.LocalDateTime local) {
            return local.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException(
                "Unsupported timestamp type from native query: " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> findAuditByCorrelation(UUID orgId, String correlationId) {
        return entityManager.createNativeQuery("""
                        SELECT created_at, action, outcome, actor_user_id, actor_email,
                               resource_type, resource_id, metadata
                        FROM audit_logs
                        WHERE (:orgId IS NULL OR organization_id = :orgId)
                          AND metadata ->> 'correlationId' = :correlationId
                        ORDER BY created_at ASC
                        """)
                .setParameter("orgId", orgId)
                .setParameter("correlationId", correlationId)
                .getResultList();
    }

    private UUID resolveOrgScope(UUID tokenOrg, boolean platformAdmin, UUID requestedOrgId) {
        if (platformAdmin) {
            return requestedOrgId != null ? requestedOrgId : tokenOrg;
        }
        if (tokenOrg == null) {
            throw ApiException.of(ErrorCode.ORGANIZATION_REQUIRED, "Select an organization");
        }
        if (requestedOrgId != null && !requestedOrgId.equals(tokenOrg)) {
            throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS, "Cannot query another organization's logs");
        }
        return tokenOrg;
    }

    private EventLogView toView(EventLog log) {
        return new EventLogView(
                log.getId().getId(),
                log.getId().getOccurredAt(),
                log.getOrganizationId(),
                log.getEventCode(),
                log.getCategory(),
                log.getSeverity(),
                log.getCorrelationId(),
                log.getActorUserId(),
                log.getActorType(),
                log.getActorLabel(),
                log.getTargetType(),
                log.getTargetId(),
                log.getPayload(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.isSecurityEvent(),
                log.isContainsPii());
    }
}
