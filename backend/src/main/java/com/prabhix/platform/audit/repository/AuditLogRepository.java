package com.prabhix.platform.audit.repository;

import com.prabhix.platform.audit.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLog.AuditLogId> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:orgId IS NULL OR a.organizationId = :orgId)
              AND (:action IS NULL OR a.action = :action)
              AND (:actorId IS NULL OR a.actorUserId = :actorId)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (CAST(:from AS timestamp) IS NULL OR a.id.createdAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR a.id.createdAt <= :to)
              AND (CAST(:cursorCreated AS timestamp) IS NULL
                   OR a.id.createdAt < :cursorCreated
                   OR (a.id.createdAt = :cursorCreated AND a.id.id < :cursorId))
            ORDER BY a.id.createdAt DESC, a.id.id DESC
            """)
    List<AuditLog> findPage(@Param("orgId") UUID organizationId,
                            @Param("action") String action,
                            @Param("actorId") UUID actorUserId,
                            @Param("resourceType") String resourceType,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            @Param("cursorCreated") Instant cursorCreated,
                            @Param("cursorId") UUID cursorId,
                            Pageable pageable);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.organizationId = :orgId
            ORDER BY a.id.createdAt DESC, a.id.id DESC
            """)
    List<AuditLog> findRecentByOrganization(@Param("orgId") UUID organizationId, Pageable pageable);
}
