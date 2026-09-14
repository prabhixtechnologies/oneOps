package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MailSuppressionRepository extends JpaRepository<MailSuppression, UUID> {

    @Query("""
            SELECT s FROM MailSuppression s
            WHERE lower(s.address) = lower(:address)
              AND (s.organizationId IS NULL OR s.organizationId = :orgId)
              AND (s.expiresAt IS NULL OR s.expiresAt > :now)
            ORDER BY CASE WHEN s.organizationId IS NOT NULL THEN 0 ELSE 1 END
            """)
    Optional<MailSuppression> findActive(String address, UUID orgId, Instant now);

    java.util.List<MailSuppression> findByOrganizationIdOrOrganizationIdIsNullOrderByAddress(UUID organizationId);

    long countByReason(com.prabhix.platform.mail.domain.MailEnums.SuppressionReason reason);
}
