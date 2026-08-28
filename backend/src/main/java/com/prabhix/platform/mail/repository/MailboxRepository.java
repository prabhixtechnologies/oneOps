package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.domain.MailEnums;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {

    Optional<Mailbox> findByAddressIgnoreCaseAndDeletedAtIsNull(String address);

    Optional<Mailbox> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    List<Mailbox> findByOrganizationIdAndDeletedAtIsNullOrderByName(UUID organizationId);

    long countByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    @Query("""
            SELECT m FROM Mailbox m
            WHERE m.status = :status AND m.imapHost IS NOT NULL AND m.deletedAt IS NULL
            ORDER BY m.imapLastPolledAt ASC NULLS FIRST
            """)
    List<Mailbox> findDueForImapPoll(MailEnums.MailboxStatus status);
}
