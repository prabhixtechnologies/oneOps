package com.prabhix.platform.observability.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only operational event row.
 *
 * <p>Does not extend {@link com.prabhix.platform.common.entity.AuditableEntity}: this table
 * is high-volume, insert-only, and retention deletes rows — {@code updated_at} /
 * {@code deleted_at} would never be used and would fail Hibernate validation if half-present.
 */
@Getter
@Setter
@Entity
@Table(name = "event_logs")
public class EventLog {

    @EmbeddedId
    private EventLogId id = new EventLogId();

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "event_code", nullable = false, length = 80)
    private String eventCode;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_type", nullable = false, length = 24)
    private String actorType = "SYSTEM";

    @Column(name = "actor_label", length = 255)
    private String actorLabel;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = Map.of();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "security_event", nullable = false)
    private boolean securityEvent;

    @Column(name = "contains_pii", nullable = false)
    private boolean containsPii;

    @Getter
    @Setter
    @Embeddable
    public static class EventLogId implements Serializable {

        @Column(name = "id", nullable = false)
        private UUID id;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EventLogId that)) {
                return false;
            }
            return Objects.equals(id, that.id) && Objects.equals(occurredAt, that.occurredAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, occurredAt);
        }
    }
}
