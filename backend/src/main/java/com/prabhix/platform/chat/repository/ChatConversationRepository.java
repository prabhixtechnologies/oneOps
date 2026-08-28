package com.prabhix.platform.chat.repository;

import com.prabhix.platform.chat.domain.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    /**
     * A conversation created from a pre-chat form has no message yet, so sorting on
     * {@code last_message_at} alone would strand it outside the keyset walk. Falling back to
     * {@code created_at} gives every row a non-null sort key and keeps the cursor a single
     * comparable value.
     */
    @Query(value = """
            SELECT c.* FROM chat_conversations c
            WHERE c.organization_id = :orgId AND c.deleted_at IS NULL
              AND (:queue = 'mine' AND c.assigned_agent_id = :agentId
                   OR :queue = 'unassigned' AND c.assigned_agent_id IS NULL
                   OR :queue = 'all')
              AND (:status IS NULL OR c.status = :status)
              AND (COALESCE(c.last_message_at, c.created_at) < :cursorAt
                   OR (COALESCE(c.last_message_at, c.created_at) = :cursorAt AND c.id < :cursorId))
            ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatConversation> listWithCursor(UUID orgId, String queue, UUID agentId, String status,
                                          Instant cursorAt, UUID cursorId, int limit);

    @Query(value = """
            SELECT count(*) FROM chat_conversations
            WHERE organization_id = :orgId AND deleted_at IS NULL
              AND assigned_agent_id = :agentId AND unread_agent_count > 0
            """, nativeQuery = true)
    long countUnreadForAgent(UUID orgId, UUID agentId);

    @Query(value = """
            SELECT count(*) FROM chat_conversations
            WHERE organization_id = :orgId AND deleted_at IS NULL
              AND assigned_agent_id IS NULL AND status IN ('OPEN', 'PENDING')
            """, nativeQuery = true)
    long countUnassigned(UUID orgId);

    @Query(value = """
            SELECT count(*) FROM chat_conversations
            WHERE organization_id = :orgId AND deleted_at IS NULL
              AND assigned_agent_id = :agentId AND status IN ('OPEN', 'PENDING')
            """, nativeQuery = true)
    long countOpenForAgent(UUID orgId, UUID agentId);
}
