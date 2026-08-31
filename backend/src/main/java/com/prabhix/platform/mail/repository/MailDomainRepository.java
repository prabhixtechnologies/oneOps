package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailDomain;
import com.prabhix.platform.mail.domain.MailEnums;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailDomainRepository extends JpaRepository<MailDomain, UUID> {

    List<MailDomain> findByOrganizationIdAndDeletedAtIsNullOrderByDomain(UUID organizationId);

    Optional<MailDomain> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Optional<MailDomain> findByDomainIgnoreCaseAndDeletedAtIsNull(String domain);

    boolean existsByDomainIgnoreCaseAndDeletedAtIsNull(String domain);

    @Query(value = """
            SELECT * FROM mail.mail_domains
            WHERE status = 'VERIFIED' AND deleted_at IS NULL
              AND (last_checked_at IS NULL OR last_checked_at < :cutoff)
            ORDER BY last_checked_at ASC NULLS FIRST
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MailDomain> claimDueForRecheck(Instant cutoff, int limit);
}
