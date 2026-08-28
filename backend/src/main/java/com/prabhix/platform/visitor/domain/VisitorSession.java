package com.prabhix.platform.visitor.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "visitor_sessions")
public class VisitorSession extends TenantScopedEntity {

    @Column(name = "visitor_id", nullable = false)
    private UUID visitorId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "entry_url", length = 2000)
    private String entryUrl;

    @Column(name = "exit_url", length = 2000)
    private String exitUrl;

    @Column(name = "referrer", length = 500)
    private String referrer;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "browser", length = 80)
    private String browser;

    @Column(name = "os", length = 80)
    private String os;

    @Column(name = "screen_width")
    private Integer screenWidth;

    @Column(name = "screen_height")
    private Integer screenHeight;

    @Column(name = "language", length = 16)
    private String language;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "geo_country", length = 2)
    private String geoCountry;

    @Column(name = "geo_region", length = 80)
    private String geoRegion;

    @Column(name = "geo_city", length = 120)
    private String geoCity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "utm", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> utm = Map.of();
}
