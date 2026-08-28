package com.prabhix.platform.site.repository;

import com.prabhix.platform.site.domain.SiteLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SiteLeadRepository extends JpaRepository<SiteLead, UUID> {

    @Query("SELECT COUNT(l) FROM SiteLead l WHERE l.email = :email AND l.createdAt >= :since")
    long countRecentByEmail(@Param("email") String email, @Param("since") Instant since);

    @Query("SELECT COUNT(l) FROM SiteLead l WHERE l.ipAddress = :ip AND l.createdAt >= :since")
    long countRecentByIp(@Param("ip") String ip, @Param("since") Instant since);

    @Query(value = """
            SELECT * FROM site_leads l
            WHERE (:status IS NULL OR l.status = :status)
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR l.created_at < CAST(:cursorAt AS timestamptz)
                OR (l.created_at = CAST(:cursorAt AS timestamptz)
                    AND l.id < CAST(:cursorId AS uuid))
              )
            ORDER BY l.created_at DESC, l.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<SiteLead> listWithCursor(
            @Param("status") String status,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
