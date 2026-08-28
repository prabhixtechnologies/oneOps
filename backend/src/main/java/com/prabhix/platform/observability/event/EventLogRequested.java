package com.prabhix.platform.observability.event;

import com.prabhix.platform.common.event.PlatformEvent;
import com.prabhix.platform.observability.taxonomy.LogCategory;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.observability.taxonomy.LogSeverity;

import java.util.Map;
import java.util.UUID;

/**
 * Persist this operational event after the publishing transaction commits.
 *
 * <p>Distinct from {@link com.prabhix.platform.common.event.AuditRequested}: audit answers
 * "who changed what for compliance"; this answers "what happened for operations".
 */
public record EventLogRequested(
        UUID organizationId,
        LogEventCode eventCode,
        LogCategory category,
        LogSeverity severity,
        String correlationId,
        UUID actorUserId,
        String actorType,
        String actorLabel,
        String targetType,
        UUID targetId,
        Map<String, Object> payload,
        String ipAddress,
        String userAgent,
        boolean securityEvent,
        boolean containsPii) implements PlatformEvent {

    public EventLogRequested {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
