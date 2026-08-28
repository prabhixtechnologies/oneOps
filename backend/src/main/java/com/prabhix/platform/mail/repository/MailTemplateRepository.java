package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, UUID> {

    @Query("""
            SELECT t FROM MailTemplate t
            WHERE t.templateKey = :key AND t.locale = :locale AND t.enabled = true
              AND (t.organizationId = :orgId OR t.organizationId IS NULL)
            ORDER BY CASE WHEN t.organizationId IS NOT NULL THEN 0 ELSE 1 END
            """)
    java.util.List<MailTemplate> resolveTemplate(String key, String locale, UUID orgId);

    java.util.List<MailTemplate> findByOrganizationIdOrOrganizationIdIsNullOrderByTemplateKey(UUID organizationId);

    Optional<MailTemplate> findByOrganizationIdAndTemplateKeyAndLocale(
            UUID organizationId, String templateKey, String locale);

    Optional<MailTemplate> findByOrganizationIdIsNullAndTemplateKeyAndLocale(String templateKey, String locale);
}
