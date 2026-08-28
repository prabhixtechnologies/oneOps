package com.prabhix.platform.visitor.repository;

import com.prabhix.platform.visitor.domain.VisitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VisitorEventRepository extends JpaRepository<VisitorEvent, UUID> {

    @Query(value = """
            SELECT e.* FROM visitor_events e
            WHERE e.organization_id = :orgId AND e.visitor_id = :visitorId
              AND (e.occurred_at < :cursorAt OR (e.occurred_at = :cursorAt AND e.id < :cursorId))
            ORDER BY e.occurred_at DESC, e.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<VisitorEvent> listForVisitorWithCursor(UUID orgId, UUID visitorId,
                                                Instant cursorAt, UUID cursorId, int limit);

    @Modifying
    @Query(value = """
            DELETE FROM visitor_events
            WHERE organization_id = :orgId AND id IN (
                SELECT id FROM visitor_events
                WHERE organization_id = :orgId AND occurred_at < :before
                ORDER BY id LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchBefore(UUID orgId, Instant before, int batchSize);
}
