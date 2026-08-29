package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailFolderRepository extends JpaRepository<MailFolder, UUID> {

    List<MailFolder> findByMailboxIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(UUID mailboxId);

    Optional<MailFolder> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<MailFolder> findByMailboxIdAndKindAndDeletedAtIsNull(UUID mailboxId,
                                                                 MailEnums.FolderKind kind);

    boolean existsByMailboxIdAndDeletedAtIsNull(UUID mailboxId);

    @Query("""
            select f from MailFolder f
            where f.mailboxId in :mailboxIds and f.deletedAt is null
            order by f.sortOrder asc, f.name asc
            """)
    List<MailFolder> findForMailboxes(@Param("mailboxIds") Collection<UUID> mailboxIds);

    /**
     * How many threads sit in each of these folders, and how many of those the reader has not opened.
     *
     * <p>One query for the whole sidebar rather than two per folder: a mailbox with twenty folders would
     * otherwise be forty round trips to draw a list of names.
     */
    @Query("""
            select tf.folderId,
                   count(tf.threadId),
                   sum(case when fl.readAt is null then 1 else 0 end)
            from MailThreadFolder tf
                     left join MailThreadFlag fl
                               on fl.id.threadId = tf.threadId and fl.id.userId = :userId
            where tf.folderId in :folderIds
            group by tf.folderId
            """)
    List<Object[]> countsByFolder(@Param("folderIds") Collection<UUID> folderIds,
                                  @Param("userId") UUID userId);

    boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);
}
