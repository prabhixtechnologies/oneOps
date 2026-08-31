package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_aliases")
public class MailAlias extends TenantScopedEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "address", nullable = false, columnDefinition = "citext")
    private String address;
}
