package com.prabhix.platform.observability.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "system_health_snapshots")
public class SystemHealthSnapshot {

    @Id
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "bucket_granularity", nullable = false, length = 8)
    private String bucketGranularity = "HOUR";

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @Column(name = "warn_count", nullable = false)
    private long warnCount;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "slow_request_count", nullable = false)
    private long slowRequestCount;

    @Column(name = "mail_outbox_pending", nullable = false)
    private long mailOutboxPending;

    @Column(name = "mail_outbox_failed", nullable = false)
    private long mailOutboxFailed;

    @Column(name = "payment_failures", nullable = false)
    private long paymentFailures;

    @Column(name = "ai_tokens_used", nullable = false)
    private long aiTokensUsed;

    @Column(name = "chat_queue_wait_ms", nullable = false)
    private long chatQueueWaitMs;

    @Column(name = "active_visitors", nullable = false)
    private long activeVisitors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_event_codes", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> topEventCodes = List.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
