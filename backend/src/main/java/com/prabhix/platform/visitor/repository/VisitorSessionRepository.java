package com.prabhix.platform.visitor.repository;

import com.prabhix.platform.visitor.domain.VisitorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitorSessionRepository extends JpaRepository<VisitorSession, UUID> {

    Optional<VisitorSession> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<VisitorSession> findByVisitorIdAndOrganizationIdOrderByStartedAtDesc(UUID visitorId, UUID organizationId);

    @Query(value = """
            SELECT s.* FROM visitor_sessions s
            WHERE s.organization_id = :orgId AND s.visitor_id = :visitorId
              AND (s.started_at < :cursorAt OR (s.started_at = :cursorAt AND s.id < :cursorId))
            ORDER BY s.started_at DESC, s.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<VisitorSession> listForVisitorWithCursor(UUID orgId, UUID visitorId,
                                                  Instant cursorAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT count(*) FROM visitor_sessions
            WHERE organization_id = :orgId AND started_at >= :since AND started_at < :until
            """, nativeQuery = true)
    long countSessionsInRange(UUID orgId, Instant since, Instant until);

    @Query(value = """
            SELECT count(*) FROM visitor_sessions
            WHERE organization_id = :orgId AND started_at >= :since
            """, nativeQuery = true)
    long countSessionsSince(UUID orgId, Instant since);

    @Query(value = """
            SELECT CAST(started_at AS date) AS day, count(*) AS cnt
            FROM visitor_sessions
            WHERE organization_id = :orgId AND started_at >= :since
            GROUP BY CAST(started_at AS date)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> countSessionsByDay(UUID orgId, Instant since);
}
