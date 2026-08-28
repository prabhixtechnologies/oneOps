package com.prabhix.platform.observability.repository;

import com.prabhix.platform.observability.domain.EventLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventLogRepository extends JpaRepository<EventLog, EventLog.EventLogId> {

    @Query("""
            SELECT e FROM EventLog e
            WHERE (:orgId IS NULL OR e.organizationId = :orgId)
              AND (:severity IS NULL OR e.severity = :severity)
              AND (:category IS NULL OR e.category = :category)
              AND (:eventCode IS NULL OR e.eventCode = :eventCode)
              AND (:correlationId IS NULL OR e.correlationId = :correlationId)
              AND (:actorUserId IS NULL OR e.actorUserId = :actorUserId)
              AND (:targetType IS NULL OR e.targetType = :targetType)
              AND (:targetId IS NULL OR e.targetId = :targetId)
              AND (CAST(:from AS timestamp) IS NULL OR e.id.occurredAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.id.occurredAt <= :to)
              AND (CAST(:cursorAt AS timestamp) IS NULL
                   OR e.id.occurredAt < :cursorAt
                   OR (e.id.occurredAt = :cursorAt AND e.id.id < :cursorId))
            ORDER BY e.id.occurredAt DESC, e.id.id DESC
            """)
    List<EventLog> findPage(@Param("orgId") UUID organizationId,
                            @Param("severity") String severity,
                            @Param("category") String category,
                            @Param("eventCode") String eventCode,
                            @Param("correlationId") String correlationId,
                            @Param("actorUserId") UUID actorUserId,
                            @Param("targetType") String targetType,
                            @Param("targetId") UUID targetId,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            @Param("cursorAt") Instant cursorAt,
                            @Param("cursorId") UUID cursorId,
                            Pageable pageable);

    @Query("""
            SELECT e FROM EventLog e
            WHERE e.organizationId = :orgId AND e.id.id = :id
            """)
    Optional<EventLog> findByOrgAndId(@Param("orgId") UUID organizationId, @Param("id") UUID id);

    @Query("""
            SELECT e FROM EventLog e
            WHERE (:orgId IS NULL OR e.organizationId = :orgId)
              AND e.correlationId = :correlationId
            ORDER BY e.id.occurredAt ASC, e.id.id ASC
            """)
    List<EventLog> findByCorrelation(@Param("orgId") UUID organizationId,
                                     @Param("correlationId") String correlationId);

    @Query(value = """
            SELECT date_trunc('hour', occurred_at) AS bucket,
                   count(*) FILTER (WHERE severity IN ('ERROR', 'FATAL')) AS errors
            FROM event_logs
            WHERE organization_id = :orgId
              AND occurred_at >= :from
              AND occurred_at <= :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countErrorsByHour(@Param("orgId") UUID organizationId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    @Query(value = """
            SELECT event_code, count(*) AS cnt
            FROM event_logs
            WHERE organization_id = :orgId
              AND occurred_at >= :from
              AND occurred_at <= :to
            GROUP BY event_code
            ORDER BY cnt DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topEventCodes(@Param("orgId") UUID organizationId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM event_logs e
            WHERE (:orgId IS NULL OR e.organization_id = :orgId)
              AND (:severity IS NULL OR e.severity = :severity)
              AND (:category IS NULL OR e.category = :category)
              AND (:eventCode IS NULL OR e.event_code = :eventCode)
              AND (:correlationId IS NULL OR e.correlation_id = :correlationId)
              AND (:actorUserId IS NULL OR e.actor_user_id = :actorUserId)
              AND (:targetType IS NULL OR e.target_type = :targetType)
              AND (:targetId IS NULL OR e.target_id = :targetId)
              AND (CAST(:from AS timestamptz) IS NULL OR e.occurred_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR e.occurred_at <= :to)
              AND (:search IS NULL OR e.payload::text ILIKE concat('%', :search, '%'))
              AND (CAST(:cursorAt AS timestamptz) IS NULL
                   OR e.occurred_at < :cursorAt
                   OR (e.occurred_at = :cursorAt AND e.id < :cursorId))
            ORDER BY e.occurred_at DESC, e.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EventLog> searchPage(@Param("orgId") UUID organizationId,
                              @Param("severity") String severity,
                              @Param("category") String category,
                              @Param("eventCode") String eventCode,
                              @Param("correlationId") String correlationId,
                              @Param("actorUserId") UUID actorUserId,
                              @Param("targetType") String targetType,
                              @Param("targetId") UUID targetId,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              @Param("search") String search,
                              @Param("cursorAt") Instant cursorAt,
                              @Param("cursorId") UUID cursorId,
                              @Param("limit") int limit);

    @Modifying
    @Query(value = """
            DELETE FROM event_logs
            WHERE (id, occurred_at) IN (
                SELECT id, occurred_at FROM event_logs
                WHERE occurred_at < :cutoff
                LIMIT :batch
            )
            """, nativeQuery = true)
    int deleteOlderThanBatch(@Param("cutoff") Instant cutoff, @Param("batch") int batch);
}
