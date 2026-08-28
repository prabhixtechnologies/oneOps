package com.prabhix.platform.visitor.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "visitor_events")
public class VisitorEvent extends TenantScopedEntity {

    @Column(name = "visitor_id", nullable = false)
    private UUID visitorId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "event_name", nullable = false, length = 120)
    private String eventName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> properties = Map.of();

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
