package com.prabhix.platform.chat.repository;

import com.prabhix.platform.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * {@code includeNotes} has to be part of the query rather than a filter applied to the
     * result: dropping internal notes after the page is fetched would leave a short page
     * whose has-more flag was computed from rows the visitor never receives, so paging
     * through a conversation with notes in it would stall or skip.
     */
    @Query(value = """
            SELECT m.* FROM chat_messages m
            WHERE m.conversation_id = :conversationId AND m.deleted_at IS NULL
              AND (:includeNotes = true OR m.sender_type <> 'NOTE')
              AND (m.occurred_at < :cursorAt OR (m.occurred_at = :cursorAt AND m.id < :cursorId))
            ORDER BY m.occurred_at DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatMessage> listWithCursor(UUID conversationId, boolean includeNotes,
                                     Instant cursorAt, UUID cursorId, int limit);

    List<ChatMessage> findByConversationIdAndDeletedAtIsNullOrderByOccurredAtAsc(UUID conversationId);

    long countByConversationIdAndSenderTypeAndDeletedAtIsNull(UUID conversationId,
                                                              com.prabhix.platform.chat.domain.ChatEnums.SenderType senderType);
}
