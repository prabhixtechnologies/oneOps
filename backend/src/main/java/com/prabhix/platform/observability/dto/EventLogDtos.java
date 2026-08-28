package com.prabhix.platform.observability.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EventLogDtos {

    private EventLogDtos() {
    }

    public record EventLogView(
            UUID id,
            Instant occurredAt,
            UUID organizationId,
            String eventCode,
            String category,
            String severity,
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
            boolean containsPii) {
    }

    public record TraceEntry(
            String source,
            Instant timestamp,
            String actionOrCode,
            String severity,
            UUID actorUserId,
            String actorLabel,
            String targetType,
            UUID targetId,
            Map<String, Object> details) {
    }

    public record TraceView(
            String correlationId,
            List<TraceEntry> entries) {
    }

    public record ErrorBucket(Instant bucketStart, long errorCount) {
    }

    public record EventCodeCount(String eventCode, long count) {
    }

    public record EventLogStats(
            List<ErrorBucket> errorsOverTime,
            List<EventCodeCount> topEventCodes) {
    }
}
