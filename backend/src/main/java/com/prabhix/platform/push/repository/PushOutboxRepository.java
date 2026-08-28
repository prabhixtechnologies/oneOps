package com.prabhix.platform.push.repository;

import com.prabhix.platform.push.domain.PushOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushOutboxRepository extends JpaRepository<PushOutbox, UUID> {

    Optional<PushOutbox> findByDedupeKey(String dedupeKey);

    @Query(value = """
            SELECT * FROM push_outbox
            WHERE status = 'PENDING' AND scheduled_at <= :now
            ORDER BY scheduled_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<PushOutbox> claimPending(Instant now, int limit);

    @Query(value = """
            SELECT * FROM push_outbox
            WHERE status = 'FAILED' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<PushOutbox> claimRetryable(Instant now, int limit);

    @Modifying
    @Query(value = """
            UPDATE push_outbox SET status = 'PENDING', claimed_at = NULL, claimed_by = NULL
            WHERE status IN ('CLAIMED', 'SENDING')
              AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int releaseStuck(Instant staleBefore);
}
