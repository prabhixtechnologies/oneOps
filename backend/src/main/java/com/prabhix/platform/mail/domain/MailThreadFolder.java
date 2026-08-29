package com.prabhix.platform.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Which folder a thread is filed in. One row per thread, so a move is an update and not a pair of
 * inserts and deletes that can half-happen.
 *
 * <p>The primary key is the thread id rather than a generated one: there is nothing else this row could
 * be keyed by, and making the relationship the key is what stops a thread from being in two places.
 */
@Getter
@Setter
@Entity
@Table(name = "mail_thread_folders")
public class MailThreadFolder {

    @Id
    @Column(name = "thread_id", nullable = false, updatable = false)
    private UUID threadId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt = Instant.now();

    @Column(name = "moved_by")
    private UUID movedBy;
}
