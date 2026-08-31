package com.prabhix.platform.mail.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(schema = "mail", name = "mail_tags")
public class MailTag extends TenantScopedEntity {

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "colour", nullable = false, length = 9)
    private String colour = "#7C3AED";

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;
}
