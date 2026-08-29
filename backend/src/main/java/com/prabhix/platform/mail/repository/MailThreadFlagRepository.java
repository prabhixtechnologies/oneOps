package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailThreadFlagRepository
        extends JpaRepository<MailThreadFlag, MailThreadFlag.Key> {

    Optional<MailThreadFlag> findByIdThreadIdAndIdUserId(UUID threadId, UUID userId);

    @Query("""
            select f from MailThreadFlag f
            where f.id.userId = :userId and f.id.threadId in :threadIds
            """)
    List<MailThreadFlag> findForThreads(@Param("userId") UUID userId,
                                        @Param("threadIds") Collection<UUID> threadIds);

    @Query("""
            select f from MailThreadFlag f
            where f.organizationId = :orgId and f.id.userId = :userId and f.starredAt is not null
            order by f.starredAt desc
            """)
    List<MailThreadFlag> findStarred(@Param("orgId") UUID orgId, @Param("userId") UUID userId);

    /**
     * Marks a thread unread for everybody who had read it, because a new message has arrived.
     *
     * <p>Absence of a row already means unread, so this only has to clear the rows that say otherwise —
     * there is no need to create a row per reader per thread just to record the default.
     */
    @Modifying
    @Query("""
            update MailThreadFlag f
            set f.readAt = null, f.updatedAt = :now
            where f.id.threadId = :threadId and f.readAt is not null
            """)
    int markUnreadForAll(@Param("threadId") UUID threadId, @Param("now") Instant now);

    /** Threads whose snooze has expired and which therefore belong back in the inbox. */
    @Query("""
            select f from MailThreadFlag f
            where f.snoozedUntil is not null and f.snoozedUntil <= :now
            """)
    List<MailThreadFlag> findDueSnoozes(@Param("now") Instant now);
}
