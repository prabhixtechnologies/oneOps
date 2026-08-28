package com.prabhix.platform.site.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "site_subscribers")
public class SiteSubscriber extends AuditableEntity {

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "name", length = 160)
    private String name;

    @Column(name = "source", nullable = false, length = 80)
    private String source = "footer";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SiteEnums.SubscriberStatus status = SiteEnums.SubscriberStatus.PENDING;

    @Column(name = "confirm_token_hash", length = 64)
    private String confirmTokenHash;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    @Column(name = "unsubscribe_token", nullable = false, length = 80, unique = true)
    private String unsubscribeToken;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
