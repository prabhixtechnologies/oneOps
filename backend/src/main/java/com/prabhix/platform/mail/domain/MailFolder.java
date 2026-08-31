package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A place in a mailbox where threads are filed.
 *
 * <p>Every mailbox gets the six system folders when it is created, and a person can add their own
 * alongside them. Deleting a system folder is refused, because the alternative is a mailbox with
 * nowhere to put deleted mail.
 */
@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_folders")
public class MailFolder extends TenantScopedEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private MailEnums.FolderKind kind = MailEnums.FolderKind.CUSTOM;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "colour", length = 9)
    private String colour;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
