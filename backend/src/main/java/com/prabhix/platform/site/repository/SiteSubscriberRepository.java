package com.prabhix.platform.site.repository;

import com.prabhix.platform.site.domain.SiteEnums;
import com.prabhix.platform.site.domain.SiteSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteSubscriberRepository extends JpaRepository<SiteSubscriber, UUID> {

    Optional<SiteSubscriber> findByEmail(String email);

    Optional<SiteSubscriber> findByConfirmTokenHashAndStatus(String confirmTokenHash,
                                                             SiteEnums.SubscriberStatus status);

    Optional<SiteSubscriber> findByUnsubscribeToken(String unsubscribeToken);

    @Query(value = """
            SELECT * FROM site_subscribers s
            WHERE (:status IS NULL OR s.status = :status)
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR s.created_at < CAST(:cursorAt AS timestamptz)
                OR (s.created_at = CAST(:cursorAt AS timestamptz)
                    AND s.id < CAST(:cursorId AS uuid))
              )
            ORDER BY s.created_at DESC, s.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<SiteSubscriber> listWithCursor(
            @Param("status") String status,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
