package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailInboundRaw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MailInboundRawRepository extends JpaRepository<MailInboundRaw, UUID> {

    @Query(value = """
            SELECT * FROM mail_inbound_raw
            WHERE status = 'PENDING'
            ORDER BY received_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MailInboundRaw> claimPending(int limit);
}
