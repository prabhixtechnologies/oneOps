package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailTagRepository extends JpaRepository<MailTag, UUID> {

    List<MailTag> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<MailTag> findByOrganizationIdAndSlug(UUID organizationId, String slug);

    Optional<MailTag> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<MailTag> findByOrganizationIdAndSlugIn(UUID organizationId, java.util.Collection<String> slugs);
}
