package com.prabhix.platform.visitor.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "visitor_page_views")
public class VisitorPageView extends TenantScopedEntity {

    @Column(name = "visitor_id", nullable = false)
    private UUID visitorId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "url", nullable = false, length = 2000)
    private String url;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "referrer", length = 500)
    private String referrer;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt = Instant.now();

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "is_entry", nullable = false)
    private boolean entry;

    @Column(name = "is_exit", nullable = false)
    private boolean exit;
}
