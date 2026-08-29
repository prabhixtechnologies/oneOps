package com.prabhix.platform.observability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.dto.EventLogDtos.EventLogView;
import com.prabhix.platform.observability.repository.EventLogRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventLogExportService {

    private static final int MAX_EXPORT = 10_000;

    private final EventLogQueryService queryService;
    private final EventLogRepository eventLogRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final PrabhixProperties properties;

    @Transactional(readOnly = true)
    public void exportCsv(UUID organizationId,
                          boolean platformAdmin,
                          UUID requestedOrgId,
                          boolean allOrganizations,
                          String severity,
                          String category,
                          String eventCode,
                          Instant from,
                          Instant to,
                          OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("occurredAt,eventCode,severity,category,correlationId,actorLabel,targetType,targetId");
        writeRows(organizationId, platformAdmin, requestedOrgId, allOrganizations, severity, category, eventCode,
                from, to, writer, (view) -> String.join(",",
                        csv(view.occurredAt()),
                        csv(view.eventCode()),
                        csv(view.severity()),
                        csv(view.category()),
                        csv(view.correlationId()),
                        csv(view.actorLabel()),
                        csv(view.targetType()),
                        view.targetId() == null ? "" : view.targetId().toString()));
        writer.flush();
    }

    @Transactional(readOnly = true)
    public void exportNdjson(UUID organizationId,
                             boolean platformAdmin,
                             UUID requestedOrgId,
                             boolean allOrganizations,
                             String severity,
                             String category,
                             String eventCode,
                             Instant from,
                             Instant to,
                             OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writeRows(organizationId, platformAdmin, requestedOrgId, allOrganizations, severity, category, eventCode,
                from, to, writer, view -> {
                    try {
                        return objectMapper.writeValueAsString(view);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
        writer.flush();
    }

    @SuppressWarnings("unchecked")
    private void writeRows(UUID organizationId,
                           boolean platformAdmin,
                           UUID requestedOrgId,
                           boolean allOrganizations,
                           String severity,
                           String category,
                           String eventCode,
                           Instant from,
                           Instant to,
                           PrintWriter writer,
                           java.util.function.Function<EventLogView, String> formatter) {
        String cursor = null;
        int exported = 0;
        while (exported < MAX_EXPORT) {
            int batch = Math.min(500, MAX_EXPORT - exported);
            var page = queryService.search(
                    organizationId, platformAdmin, requestedOrgId,
                    severity, category, eventCode, null, null, null, null,
                    from, to, null, allOrganizations, cursor, batch);
            for (EventLogView view : page.items()) {
                writer.println(formatter.apply(view));
                exported++;
            }
            if (!page.hasMore() || page.nextCursor() == null) {
                break;
            }
            cursor = page.nextCursor();
        }
    }

    @Transactional(readOnly = true)
    public List<Object[]> findAuditByCorrelation(UUID orgId, String correlationId) {
        return entityManager.createNativeQuery("""
                        SELECT created_at, action, outcome, actor_user_id, actor_email,
                               resource_type, resource_id, metadata, changes
                        FROM audit_logs
                        WHERE (:orgId IS NULL OR organization_id = :orgId)
                          AND metadata ->> 'correlationId' = :correlationId
                        ORDER BY created_at ASC
                        """)
                .setParameter("orgId", orgId)
                .setParameter("correlationId", correlationId)
                .getResultList();
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
