package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MailThreadTagRepository extends JpaRepository<MailThreadTag, MailThreadTag.Id> {

    /**
     * The tags on a page of threads, in one query.
     *
     * <p>Takes a collection of thread ids rather than one, because the alternative is a query per row
     * of the queue. {@code findByIdThreadId} is fine for a single thread and wrong for a list of
     * fifty, and the summary mapper that needs this runs once per row.
     *
     * <p>Joined to {@link com.prabhix.platform.mail.domain.MailTag} rather than returning the join
     * rows, because a tag chip needs the name and colour and the join table holds neither.
     */
    @Query("""
            SELECT tt.id.threadId AS threadId,
                   t.id           AS tagId,
                   t.slug         AS slug,
                   t.name         AS name,
                   t.colour       AS colour
              FROM MailThreadTag tt
              JOIN MailTag t ON t.id = tt.id.tagId
             WHERE tt.id.threadId IN :threadIds
               AND t.deletedAt IS NULL
             ORDER BY t.name ASC
            """)
    List<ThreadTagView> findTagsForThreads(@Param("threadIds") Collection<UUID> threadIds);

    interface ThreadTagView {
        UUID getThreadId();

        UUID getTagId();

        String getSlug();

        String getName();

        String getColour();
    }

    List<MailThreadTag> findByIdThreadId(UUID threadId);

    boolean existsByIdThreadIdAndIdTagId(UUID threadId, UUID tagId);

    void deleteByIdTagId(UUID tagId);

    void deleteByIdThreadIdAndIdTagId(UUID threadId, UUID tagId);

    int countByIdTagId(UUID tagId);
}
