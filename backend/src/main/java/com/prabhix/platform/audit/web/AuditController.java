package com.prabhix.platform.audit.web;

import com.prabhix.platform.audit.dto.AuditDtos.AuditLogView;
import com.prabhix.platform.audit.service.AuditService;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final PrabhixProperties properties;

    @GetMapping
    @PreAuthorize(Authorize.AUDIT_READ)
    public CursorPage<AuditLogView> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        int pageSize = properties.limits().clampPageSize(limit);
        return auditService.list(
                principal.requireOrganizationId(),
                action,
                actorUserId,
                resourceType,
                from,
                to,
                cursor,
                pageSize);
    }
}
