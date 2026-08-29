package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MailThreadFolderRepository extends JpaRepository<MailThreadFolder, UUID> {

    List<MailThreadFolder> findByThreadIdIn(Collection<UUID> threadIds);

    long countByFolderId(UUID folderId);

    /**
     * Empties a folder into another one. Used when a custom folder is deleted: its threads go to the
     * inbox rather than vanishing with it, because a folder is a label and deleting a label should not
     * delete the mail.
     */
    @Modifying
    @Query("""
            update MailThreadFolder tf
            set tf.folderId = :target, tf.movedAt = current_timestamp
            where tf.folderId = :source
            """)
    int reassign(@Param("source") UUID source, @Param("target") UUID target);
}
