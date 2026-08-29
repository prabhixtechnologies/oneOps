package com.prabhix.platform.observability.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.dto.EventLogDtos.EventLogStats;
import com.prabhix.platform.observability.dto.EventLogDtos.EventLogView;
import com.prabhix.platform.observability.dto.EventLogDtos.TraceView;
import com.prabhix.platform.observability.service.EventLogExportService;
import com.prabhix.platform.observability.service.EventLogQueryService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/event-logs")
@RequiredArgsConstructor
public class EventLogController {

    private final EventLogQueryService queryService;
    private final EventLogExportService exportService;
    private final PrabhixProperties properties;

    @GetMapping
    @PreAuthorize(Authorize.LOG_READ)
    public CursorPage<EventLogView> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID organizationId,
            // Platform staff only. Widens the search to every organization, which is how the admin
            // console answers "where are the errors" without already knowing which tenant to ask.
            @RequestParam(defaultValue = "false") boolean allOrganizations,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        int pageSize = properties.limits().clampPageSize(limit);
        return queryService.search(
                principal.organizationId(),
                principal.platformAdmin(),
                organizationId,
                severity, category, eventCode, correlationId,
                actorUserId, targetType, targetId,
                from, to, search, allOrganizations, cursor, pageSize);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorize.LOG_READ)
    public EventLogView get(
            @CurrentUser PrabhixPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "false") boolean allOrganizations) {
        return queryService.getById(
                principal.organizationId(), principal.platformAdmin(), organizationId,
                allOrganizations, id);
    }

    @GetMapping("/trace/{correlationId}")
    @PreAuthorize(Authorize.LOG_READ)
    public TraceView trace(
            @CurrentUser PrabhixPrincipal principal,
            @PathVariable String correlationId,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "false") boolean allOrganizations) {
        return queryService.trace(
                principal.organizationId(), principal.platformAdmin(), organizationId,
                allOrganizations, correlationId);
    }

    @GetMapping("/stats")
    @PreAuthorize(Authorize.LOG_READ)
    public EventLogStats stats(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "false") boolean allOrganizations) {
        return queryService.stats(
                principal.organizationId(), principal.platformAdmin(), organizationId,
                allOrganizations, from, to);
    }

    @GetMapping("/export")
    @PreAuthorize(Authorize.LOG_EXPORT)
    public void export(
            @CurrentUser PrabhixPrincipal principal,
            HttpServletResponse response,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "false") boolean allOrganizations) throws IOException {
        boolean ndjson = "ndjson".equalsIgnoreCase(format);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"event-logs." + (ndjson ? "ndjson" : "csv") + "\"");
        response.setContentType(ndjson ? "application/x-ndjson" : "text/csv");
        if (ndjson) {
            exportService.exportNdjson(
                    principal.organizationId(), principal.platformAdmin(), organizationId,
                    allOrganizations, severity, category, eventCode, from, to, response.getOutputStream());
        } else {
            exportService.exportCsv(
                    principal.organizationId(), principal.platformAdmin(), organizationId,
                    allOrganizations, severity, category, eventCode, from, to, response.getOutputStream());
        }
    }
}
