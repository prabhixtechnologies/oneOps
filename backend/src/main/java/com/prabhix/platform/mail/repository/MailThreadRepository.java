package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailEnums;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailThreadRepository extends JpaRepository<MailThread, UUID> {

    Optional<MailThread> findByReferenceKey(String referenceKey);

    Optional<MailThread> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    @Query(value = """
            SELECT * FROM mail.mail_threads t
            WHERE t.mailbox_id = :mailboxId
              AND t.normalized_subject = :normalizedSubject
              AND t.deleted_at IS NULL
              AND t.status NOT IN ('CLOSED', 'TRASH', 'SPAM')
              AND t.created_at >= :since
            ORDER BY t.created_at DESC
            LIMIT 20
            """, nativeQuery = true)
    List<MailThread> findSubjectFallbackCandidates(UUID mailboxId, String normalizedSubject, Instant since);

    @Query(value = """
            SELECT t.* FROM mail.mail_threads t
            WHERE t.organization_id = :orgId AND t.deleted_at IS NULL
              AND (:mailboxId IS NULL OR t.mailbox_id = :mailboxId)
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:assigneeUserId IS NULL OR t.assignee_user_id = :assigneeUserId)
              AND (:assigneeTeamId IS NULL OR t.assignee_team_id = :assigneeTeamId)
              AND (:tagId IS NULL OR EXISTS (
                  SELECT 1 FROM mail.mail_thread_tags tt
                  WHERE tt.thread_id = t.id AND tt.tag_id = :tagId))
              AND (:unreadOnly = false OR t.unread_count > 0)
              AND (:hasAttachment = false OR t.has_attachments = true)
              AND (:readAll = true OR t.mailbox_id = ANY(:mailboxIds))
              AND (t.last_message_at < :cursorAt
                   OR (t.last_message_at = :cursorAt AND t.id < :cursorId))
            ORDER BY t.last_message_at DESC, t.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<MailThread> listWithCursor(UUID orgId, UUID mailboxId, String status, String priority,
                                    UUID assigneeUserId, UUID assigneeTeamId, UUID tagId,
                                    boolean unreadOnly, boolean hasAttachment, boolean readAll,
                                    UUID[] mailboxIds, Instant cursorAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT t.* FROM mail.mail_threads t
            WHERE t.organization_id = :orgId AND t.deleted_at IS NULL
              AND (:readAll = true OR t.mailbox_id = ANY(:mailboxIds))
              AND (:tagId IS NULL OR EXISTS (
                  SELECT 1 FROM mail.mail_thread_tags tt
                  WHERE tt.thread_id = t.id AND tt.tag_id = :tagId))
              AND to_tsvector('simple', coalesce(t.subject, '') || ' ' || coalesce(t.snippet, ''))
                  @@ plainto_tsquery('simple', :query)
              AND (t.last_message_at < :cursorAt
                   OR (t.last_message_at = :cursorAt AND t.id < :cursorId))
            ORDER BY t.last_message_at DESC, t.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<MailThread> searchWithCursor(UUID orgId, boolean readAll, UUID[] mailboxIds, UUID tagId,
                                      String query, Instant cursorAt, UUID cursorId, int limit);

    /**
     * What is in a folder, newest activity first.
     *
     * <p>Offset paging rather than the keyset paging the helpdesk list uses: a mail client's folder view
     * has a scrollbar and jumps to page 40, which a cursor cannot express. Folders are bounded by one
     * mailbox, so the offsets stay small enough that the difference does not matter.
     */
    @Query(value = """
            SELECT t.* FROM mail.mail_threads t
                     JOIN mail.mail_thread_folders tf ON tf.thread_id = t.id
            WHERE t.organization_id = :orgId AND t.deleted_at IS NULL
              AND tf.folder_id = :folderId
            ORDER BY t.last_message_at DESC, t.id DESC
            LIMIT :limit OFFSET :skip
            """, nativeQuery = true)
    List<MailThread> findInFolder(UUID orgId, UUID folderId, int limit, int skip);

    @Query("""
            SELECT t FROM MailThread t
            WHERE t.slaDueAt IS NOT NULL AND t.slaBreachedAt IS NULL
              AND t.status IN ('OPEN', 'ON_HOLD') AND t.deletedAt IS NULL
              AND t.slaDueAt <= :now
            ORDER BY t.slaDueAt ASC
            """)
    List<MailThread> findSlaBreaches(Instant now, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE MailThread t SET t.status = :status
            WHERE t.organizationId = :orgId AND t.id IN :ids AND t.deletedAt IS NULL
            """)
    int bulkUpdateStatus(UUID orgId, List<UUID> ids, MailEnums.ThreadStatus status);

    @Modifying
    @Query("""
            UPDATE MailThread t SET t.priority = :priority
            WHERE t.organizationId = :orgId AND t.id IN :ids AND t.deletedAt IS NULL
            """)
    int bulkUpdatePriority(UUID orgId, List<UUID> ids, MailEnums.Priority priority);

    @Modifying
    @Query("""
            UPDATE MailThread t SET t.slaPausedAt = :pausedAt
            WHERE t.organizationId = :orgId AND t.id IN :ids AND t.deletedAt IS NULL
              AND t.slaPausedAt IS NULL
            """)
    int bulkPauseSla(UUID orgId, List<UUID> ids, Instant pausedAt);

    @Modifying
    @Query("""
            UPDATE MailThread t SET t.assigneeUserId = :userId, t.assigneeTeamId = null,
                t.assignedAt = :now, t.assignedBy = :by
            WHERE t.id = :threadId AND t.assigneeUserId IS NULL AND t.assigneeTeamId IS NULL
            """)
    int claimIfUnassigned(UUID threadId, UUID userId, Instant now, UUID by);

    @Query("""
            SELECT COUNT(t) FROM MailThread t
            WHERE t.organizationId = :orgId AND t.deletedAt IS NULL
              AND t.status IN ('OPEN', 'PENDING', 'PENDING_CUSTOMER', 'ON_HOLD')
            """)
    long countOpenThreads(UUID orgId);

    @Query("""
            SELECT COUNT(t) FROM MailThread t
            WHERE t.organizationId = :orgId AND t.deletedAt IS NULL AND t.slaBreachedAt IS NOT NULL
              AND t.slaBreachedAt >= :since
            """)
    long countRecentSlaBreaches(UUID orgId, Instant since);

    @Query(value = """
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (t.first_response_at - t.created_at)) / 60.0), 0)
            FROM mail.mail_threads t
            WHERE t.organization_id = :orgId AND t.deleted_at IS NULL
              AND t.first_response_at IS NOT NULL
              AND t.created_at >= :since
            """, nativeQuery = true)
    double avgFirstResponseMinutes(UUID orgId, Instant since);

    @Query(value = """
            SELECT CAST(t.created_at AS date) AS day, COUNT(*) AS cnt
            FROM mail.mail_threads t
            WHERE t.organization_id = :orgId AND t.deleted_at IS NULL
              AND t.created_at >= :since
            GROUP BY CAST(t.created_at AS date)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> countThreadsByDay(UUID orgId, Instant since);

    @Query(value = """
            SELECT CAST(t.first_response_at AS date) AS day,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (t.first_response_at - t.created_at)) / 60.0), 0) AS avg_mins
            FROM mail.mail_threads t
            WHERE t.organization_id = :orgId AND t.deleted_at IS NULL
              AND t.first_response_at IS NOT NULL AND t.first_response_at >= :since
            GROUP BY CAST(t.first_response_at AS date)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> avgResponseMinutesByDay(UUID orgId, Instant since);
}
