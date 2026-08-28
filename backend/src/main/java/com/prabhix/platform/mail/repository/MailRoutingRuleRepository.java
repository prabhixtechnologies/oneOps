package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailRoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MailRoutingRuleRepository extends JpaRepository<MailRoutingRule, UUID> {

    @Query("""
            SELECT r FROM MailRoutingRule r
            WHERE r.organizationId = :orgId AND r.enabled = true
              AND (r.mailboxId IS NULL OR r.mailboxId = :mailboxId)
            ORDER BY r.priority ASC
            """)
    List<MailRoutingRule> findActiveForMailbox(UUID orgId, UUID mailboxId);

    List<MailRoutingRule> findByOrganizationIdAndMailboxIdOrderByPriorityAsc(
            UUID organizationId, UUID mailboxId);

    @Modifying
    @Query("UPDATE MailRoutingRule r SET r.matchCount = r.matchCount + 1, r.lastMatchedAt = :at WHERE r.id = :id")
    void incrementMatchCount(UUID id, Instant at);
}
