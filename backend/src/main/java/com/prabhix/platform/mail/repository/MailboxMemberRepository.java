package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailboxMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MailboxMemberRepository extends JpaRepository<MailboxMember, UUID> {

    @Query("""
            SELECT m.mailboxId FROM MailboxMember m
            WHERE m.organizationId = :orgId
              AND (m.userId = :userId OR m.teamId IN :teamIds)
            """)
    List<UUID> findAccessibleMailboxIds(UUID orgId, UUID userId, List<UUID> teamIds);

    List<MailboxMember> findByMailboxId(UUID mailboxId);

    boolean existsByMailboxIdAndUserId(UUID mailboxId, UUID userId);

    java.util.Optional<MailboxMember> findByMailboxIdAndUserId(UUID mailboxId, UUID userId);
}
