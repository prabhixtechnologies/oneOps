package com.prabhix.platform.observability.repository;

import com.prabhix.platform.observability.domain.SystemHealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SystemHealthSnapshotRepository extends JpaRepository<SystemHealthSnapshot, UUID> {

    @Query("""
            SELECT s FROM SystemHealthSnapshot s
            WHERE (:orgId IS NULL AND s.organizationId IS NULL OR s.organizationId = :orgId)
              AND s.bucketStart >= :from
              AND s.bucketStart <= :to
            ORDER BY s.bucketStart ASC
            """)
    List<SystemHealthSnapshot> findRange(@Param("orgId") UUID organizationId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);
}
