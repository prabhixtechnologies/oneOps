package com.prabhix.platform.ai.repository;

import com.prabhix.platform.ai.domain.AiPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiPromptRepository extends JpaRepository<AiPrompt, UUID> {

    @Query("""
            SELECT p FROM AiPrompt p
            WHERE p.taskKey = :taskKey AND p.enabled = true
              AND (p.organizationId = :orgId OR p.organizationId IS NULL)
            ORDER BY CASE WHEN p.organizationId IS NOT NULL THEN 0 ELSE 1 END
            """)
    List<AiPrompt> resolvePrompt(String taskKey, UUID orgId);

    List<AiPrompt> findByOrganizationIdOrOrganizationIdIsNullOrderByTaskKey(UUID organizationId);

    Optional<AiPrompt> findByOrganizationIdAndTaskKey(UUID organizationId, String taskKey);
}
