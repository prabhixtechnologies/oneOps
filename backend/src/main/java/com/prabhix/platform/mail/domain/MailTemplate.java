package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_templates")
public class MailTemplate extends AuditableEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "template_key", nullable = false, length = 80)
    private String templateKey;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale = "en";

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", nullable = false, columnDefinition = "jsonb")
    private String variables = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24)
    private MailEnums.TemplateCategory category = MailEnums.TemplateCategory.TRANSACTIONAL;

    @Column(name = "tracking_enabled", nullable = false)
    private boolean trackingEnabled;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
