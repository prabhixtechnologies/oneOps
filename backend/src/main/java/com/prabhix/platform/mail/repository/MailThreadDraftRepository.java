package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailThreadDraftRepository extends JpaRepository<MailThreadDraft, UUID> {

    Optional<MailThreadDraft> findByThreadIdAndAuthorUserId(UUID threadId, UUID authorUserId);

    List<MailThreadDraft> findByThreadId(UUID threadId);

    Optional<MailThreadDraft> findByIdAndOrganizationIdAndAuthorUserId(UUID id, UUID organizationId,
                                                                      UUID authorUserId);

    /**
     * Everything this person has left unsent, newest first. Scoped to the author rather than the
     * mailbox, because a draft belongs to whoever is writing it and nobody else should read it — not
     * even another member of the same shared mailbox.
     */
    @Query("""
            select d from MailThreadDraft d
            where d.organizationId = :orgId and d.authorUserId = :userId
            order by d.updatedAt desc
            """)
    List<MailThreadDraft> findMine(@Param("orgId") UUID orgId, @Param("userId") UUID userId);
}
