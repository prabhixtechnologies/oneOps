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

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_thread_tags")
public class MailThreadTag {

    @EmbeddedId
    private Id id = new Id();

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "applied_by")
    private UUID appliedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "thread_id")
        private UUID threadId;

        @Column(name = "tag_id")
        private UUID tagId;
    }
}
