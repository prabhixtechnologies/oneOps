package com.prabhix.platform.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One reader's view of one thread: whether they have read it, whether they starred it, and whether they
 * pushed it out of the way until later.
 *
 * <p>These are per-person on purpose. {@code mail_threads.unread_count} is a mailbox-level summary and
 * stays the number the helpdesk queue shows, but it cannot answer "is this unread" for a shared mailbox
 * with four people in it — four different answers are all correct at once.
 */
@Getter
@Setter
@Entity
@Table(name = "mail_thread_flags")
public class MailThreadFlag {

    @EmbeddedId
    private Key id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "starred_at")
    private Instant starredAt;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isStarred() {
        return starredAt != null;
    }

    public static MailThreadFlag of(UUID threadId, UUID userId, UUID organizationId) {
        MailThreadFlag flag = new MailThreadFlag();
        flag.setId(new Key(threadId, userId));
        flag.setOrganizationId(organizationId);
        return flag;
    }

    @Getter
    @Setter
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "thread_id", nullable = false, updatable = false)
        private UUID threadId;

        @Column(name = "user_id", nullable = false, updatable = false)
        private UUID userId;
    }
}
