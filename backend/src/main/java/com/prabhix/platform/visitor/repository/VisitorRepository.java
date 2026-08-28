package com.prabhix.platform.visitor.repository;

import com.prabhix.platform.visitor.domain.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitorRepository extends JpaRepository<Visitor, UUID> {

    Optional<Visitor> findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(UUID organizationId, String externalKey);

    Optional<Visitor> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<Visitor> findFirstByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNullAndMergedIntoIdIsNull(
            UUID organizationId, String email);

    @Query(value = """
            SELECT v.* FROM visitors v
            WHERE v.organization_id = :orgId AND v.deleted_at IS NULL AND v.merged_into_id IS NULL
              AND (v.last_seen_at < :cursorAt OR (v.last_seen_at = :cursorAt AND v.id < :cursorId))
            ORDER BY v.last_seen_at DESC, v.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Visitor> listWithCursor(UUID orgId, Instant cursorAt, UUID cursorId, int limit);

    // These bulk updates bypass the persistence context, so they flush pending state first
    // and clear afterwards. Without that, a managed Visitor loaded before the merge would
    // still look un-merged and could be written back over the tombstone on commit.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE visitors SET merged_into_id = :targetId, deleted_at = now(), updated_at = now()
            WHERE organization_id = :orgId AND id = :sourceId
            """, nativeQuery = true)
    int mergeVisitor(UUID orgId, UUID sourceId, UUID targetId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE visitor_sessions SET visitor_id = :targetId
            WHERE organization_id = :orgId AND visitor_id = :sourceId
            """, nativeQuery = true)
    int reassignSessions(UUID orgId, UUID sourceId, UUID targetId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE visitor_page_views SET visitor_id = :targetId
            WHERE organization_id = :orgId AND visitor_id = :sourceId
            """, nativeQuery = true)
    int reassignPageViews(UUID orgId, UUID sourceId, UUID targetId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE visitor_events SET visitor_id = :targetId
            WHERE organization_id = :orgId AND visitor_id = :sourceId
            """, nativeQuery = true)
    int reassignEvents(UUID orgId, UUID sourceId, UUID targetId);
}
