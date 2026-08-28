package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, UUID> {

    /** Backed by the partial unique index on dedupe_key. */
    Optional<MailOutbox> findByDedupeKey(String dedupeKey);

    Optional<MailOutbox> findFirstByProviderMessageId(String providerMessageId);

    @Query(value = """
            SELECT * FROM mail_outbox
            WHERE status = 'PENDING' AND scheduled_at <= :now
            ORDER BY priority, scheduled_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MailOutbox> claimPending(Instant now, int limit);

    @Query(value = """
            SELECT * FROM mail_outbox
            WHERE status = 'FAILED' AND next_attempt_at <= :now
            ORDER BY priority, next_attempt_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MailOutbox> claimRetryable(Instant now, int limit);

    @Modifying
    @Query(value = """
            UPDATE mail_outbox SET status = 'PENDING', claimed_at = NULL, claimed_by = NULL
            WHERE status IN ('CLAIMED', 'SENDING')
              AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int releaseStuck(Instant staleBefore);

    @Query(value = """
            INSERT INTO mail_outbox (id, version, organization_id, template_key, locale,
                template_variables, from_address, from_name, reply_to, to_addresses,
                dedupe_key, priority, status, scheduled_at, max_attempts, created_at, updated_at)
            VALUES (gen_random_uuid(), 0, :orgId, :templateKey, :locale, CAST(:vars AS jsonb),
                :fromAddress, :fromName, :replyTo, CAST(:to AS jsonb),
                :dedupeKey, :priority, 'PENDING', now(), :maxAttempts, now(), now())
            ON CONFLICT (dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    UUID insertWithDedupe(UUID orgId, String templateKey, String locale, String vars,
                          String fromAddress, String fromName, String replyTo, String to,
                          String dedupeKey, int priority, int maxAttempts);

    @Modifying
    @Query(value = """
            INSERT INTO mail_outbox (id, version, organization_id, mailbox_id, thread_id, message_id,
                from_address, from_name, reply_to, to_addresses, cc_addresses, bcc_addresses,
                subject, body_html, body_text, headers, attachment_ids, dedupe_key, priority,
                status, scheduled_at, max_attempts, created_at, updated_at)
            VALUES (gen_random_uuid(), 0, :orgId, :mailboxId, :threadId, :messageId,
                :fromAddress, :fromName, :replyTo, CAST(:to AS jsonb), CAST(:cc AS jsonb),
                CAST(:bcc AS jsonb), :subject, :bodyHtml, :bodyText, CAST(:headers AS jsonb),
                CAST(:attachmentIds AS jsonb), :dedupeKey, :priority, 'PENDING', now(), :maxAttempts,
                now(), now())
            ON CONFLICT (dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    UUID insertDirectWithDedupe(UUID orgId, UUID mailboxId, UUID threadId, UUID messageId,
                                String fromAddress, String fromName, String replyTo,
                                String to, String cc, String bcc, String subject,
                                String bodyHtml, String bodyText, String headers,
                                String attachmentIds, String dedupeKey, int priority,
                                int maxAttempts);
}
