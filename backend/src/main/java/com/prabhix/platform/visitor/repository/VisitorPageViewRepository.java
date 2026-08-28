package com.prabhix.platform.visitor.repository;

import com.prabhix.platform.visitor.domain.VisitorPageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VisitorPageViewRepository extends JpaRepository<VisitorPageView, UUID> {

    @Query(value = """
            SELECT p.* FROM visitor_page_views p
            WHERE p.organization_id = :orgId AND p.visitor_id = :visitorId
              AND (p.viewed_at < :cursorAt OR (p.viewed_at = :cursorAt AND p.id < :cursorId))
            ORDER BY p.viewed_at DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<VisitorPageView> listForVisitorWithCursor(UUID orgId, UUID visitorId,
                                                   Instant cursorAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT path, count(*) AS cnt FROM visitor_page_views
            WHERE organization_id = :orgId AND viewed_at >= :since
            GROUP BY path ORDER BY cnt DESC LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topPages(UUID orgId, Instant since, int limit);

    @Modifying
    @Query(value = """
            DELETE FROM visitor_page_views
            WHERE organization_id = :orgId AND id IN (
                SELECT id FROM visitor_page_views
                WHERE organization_id = :orgId AND viewed_at < :before
                ORDER BY id LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchBefore(UUID orgId, Instant before, int batchSize);
}
