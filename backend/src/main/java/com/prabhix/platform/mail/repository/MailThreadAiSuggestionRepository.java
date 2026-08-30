package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadAiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MailThreadAiSuggestionRepository extends JpaRepository<MailThreadAiSuggestion, UUID> {

    Optional<MailThreadAiSuggestion> findByThreadIdAndOrganizationId(UUID threadId, UUID organizationId);
}
