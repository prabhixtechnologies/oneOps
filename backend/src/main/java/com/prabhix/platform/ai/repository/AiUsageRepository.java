package com.prabhix.platform.ai.repository;

import com.prabhix.platform.ai.domain.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

    @Query(value = """
            SELECT u.* FROM ai_usage u
            WHERE u.organization_id = :orgId
              AND (u.created_at < :cursorAt
                   OR (u.created_at = :cursorAt AND u.id < :cursorId))
            ORDER BY u.created_at DESC, u.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AiUsage> listWithCursor(UUID orgId, Instant cursorAt, UUID cursorId, int limit);

    @Query("""
            SELECT COALESCE(SUM(u.totalTokens), 0) FROM AiUsage u
            WHERE u.organizationId = :orgId
              AND u.outcome = com.prabhix.platform.ai.domain.AiUsage.Outcome.SUCCESS
              AND u.createdAt >= :since
            """)
    long sumTokensSince(UUID orgId, Instant since);

    @Query("""
            SELECT COALESCE(SUM(u.costEstimatePaise), 0) FROM AiUsage u
            WHERE u.organizationId = :orgId
              AND u.outcome = com.prabhix.platform.ai.domain.AiUsage.Outcome.SUCCESS
              AND u.createdAt >= :since
            """)
    long sumCostSince(UUID orgId, Instant since);
}
