package com.prabhix.platform.visitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "visitor_daily_aggregates")
public class VisitorDailyAggregate {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "aggregate_date", nullable = false)
    private LocalDate aggregateDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 24)
    private VisitorEnums.AggregateMetric metricType;

    @Column(name = "dimension", nullable = false, length = 500)
    private String dimension = "";

    @Column(name = "count_value", nullable = false)
    private long countValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
