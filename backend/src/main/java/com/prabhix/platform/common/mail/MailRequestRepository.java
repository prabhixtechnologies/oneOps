package com.prabhix.platform.common.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailRequestRepository extends JpaRepository<MailRequestRecord, UUID> {

    /** Backed by the partial unique index on dedupe_key. */
    Optional<MailRequestRecord> findByDedupeKey(String dedupeKey);

    long countByStatus(MailRequestRecord.Status status);

    /**
     * Insert, or do nothing if this dedupe key was already used.
     *
     * <p>Native, rather than a read followed by a save, because the read-then-write loses the race
     * between two instances doing the same work: both find nothing, both insert, and the second
     * fails on the unique index — inside the caller's transaction, taking the business work down
     * with it. {@code ON CONFLICT DO NOTHING} makes the loser's insert a no-op instead.
     *
     * @return the new id, or {@code null} when the key was already present
     */
    @Query(value = """
            INSERT INTO mail_requests (id, version, organization_id, template_key, locale,
                to_addresses, variables, dedupe_key, priority, status, max_attempts,
                next_attempt_at, created_at, updated_at)
            VALUES (gen_random_uuid(), 0, :orgId, :templateKey, :locale, CAST(:to AS jsonb),
                CAST(:vars AS jsonb), :dedupeKey, :priority, 'PENDING', :maxAttempts,
                now(), now(), now())
            ON CONFLICT (dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    UUID insertWithDedupe(UUID orgId, String templateKey, String locale, String to, String vars,
                          String dedupeKey, int priority, int maxAttempts);

    @Query(value = """
            SELECT * FROM mail_requests
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY priority, next_attempt_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MailRequestRecord> claimPending(Instant now, int limit);

    @Query(value = """
            SELECT * FROM mail_requests
            WHERE status = 'FAILED' AND next_attempt_at <= :now
            ORDER BY priority, next_attempt_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MailRequestRecord> claimRetryable(Instant now, int limit);

    /**
     * Return rows whose claiming process never came back.
     *
     * <p>Without this a deploy in the middle of a relay pass strands every row it had claimed, in a
     * state nothing else looks at. They are not failures and would never be retried.
     */
    @Modifying
    @Query(value = """
            UPDATE mail_requests SET status = 'PENDING', claimed_at = NULL, claimed_by = NULL
            WHERE status = 'CLAIMED' AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int releaseStuck(Instant staleBefore);
}
