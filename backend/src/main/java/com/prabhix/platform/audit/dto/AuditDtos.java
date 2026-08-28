package com.prabhix.platform.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogView(
            UUID id,
            Instant createdAt,
            UUID organizationId,
            UUID actorUserId,
            String actorEmail,
            String actorType,
            String action,
            String resourceType,
            UUID resourceId,
            String resourceLabel,
            Map<String, Object> changes,
            Map<String, Object> metadata,
            String outcome) {
    }
}
