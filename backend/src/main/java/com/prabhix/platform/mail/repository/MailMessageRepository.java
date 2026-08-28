package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailMessageRepository extends JpaRepository<MailMessage, UUID> {

    List<MailMessage> findByThreadIdAndDeletedAtIsNullOrderByOccurredAtAsc(UUID threadId);

    Optional<MailMessage> findByMailboxIdAndMessageIdHeader(UUID mailboxId, String messageIdHeader);

    @Query("""
            SELECT m FROM MailMessage m
            WHERE m.messageIdHeader IN :refs AND m.deletedAt IS NULL
            """)
    List<MailMessage> findByMessageIdHeaders(List<String> refs);

    Optional<MailMessage> findFirstByThreadIdAndDeletedAtIsNullOrderByOccurredAtDesc(UUID threadId);
}
