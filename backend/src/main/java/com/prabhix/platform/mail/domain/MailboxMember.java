package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_mailbox_members")
public class MailboxMember extends TenantScopedEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "team_id")
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 16)
    private MailEnums.MemberAccessLevel accessLevel = MailEnums.MemberAccessLevel.MEMBER;

    @Column(name = "notify", nullable = false)
    private boolean notify = true;
}
