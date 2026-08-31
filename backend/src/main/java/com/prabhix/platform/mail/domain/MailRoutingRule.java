package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_routing_rules")
public class MailRoutingRule extends TenantScopedEntity {

    @Column(name = "mailbox_id")
    private UUID mailboxId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_mode", nullable = false, length = 8)
    private MailEnums.MatchMode matchMode = MailEnums.MatchMode.ALL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", nullable = false, columnDefinition = "jsonb")
    private String conditions = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", nullable = false, columnDefinition = "jsonb")
    private String actions = "[]";

    @Column(name = "continue_after_match", nullable = false)
    private boolean continueAfterMatch;

    @Column(name = "match_count", nullable = false)
    private long matchCount;

    @Column(name = "last_matched_at")
    private Instant lastMatchedAt;
}
