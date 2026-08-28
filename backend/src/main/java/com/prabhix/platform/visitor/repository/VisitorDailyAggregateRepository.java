package com.prabhix.platform.visitor.repository;

import com.prabhix.platform.visitor.domain.VisitorDailyAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VisitorDailyAggregateRepository extends JpaRepository<VisitorDailyAggregate, UUID> {

    @Query(value = """
            SELECT * FROM visitor_daily_aggregates
            WHERE organization_id = :orgId AND metric_type = :metricType
              AND aggregate_date >= :fromDate AND aggregate_date <= :toDate
            ORDER BY aggregate_date ASC
            """, nativeQuery = true)
    List<VisitorDailyAggregate> findRange(UUID orgId, String metricType, LocalDate fromDate, LocalDate toDate);
}
