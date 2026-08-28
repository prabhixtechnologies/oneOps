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

@Getter
@Setter
@Entity
@Table(name = "mail_domains")
public class MailDomain extends TenantScopedEntity {

    @Column(name = "domain", nullable = false, columnDefinition = "citext")
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MailEnums.DomainStatus status = MailEnums.DomainStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 24)
    private MailEnums.DomainMode mode = MailEnums.DomainMode.EXTERNAL_IMAP;

    @Column(name = "verification_token", nullable = false, length = 80)
    private String verificationToken;

    @Column(name = "dkim_selector", nullable = false, length = 63)
    private String dkimSelector = "pbx1";

    @Column(name = "dkim_public_key", columnDefinition = "text")
    private String dkimPublicKey;

    @Column(name = "dkim_private_key_enc", columnDefinition = "text")
    private String dkimPrivateKeyEnc;

    @Column(name = "mx_verified_at")
    private Instant mxVerifiedAt;

    @Column(name = "spf_verified_at")
    private Instant spfVerifiedAt;

    @Column(name = "dkim_verified_at")
    private Instant dkimVerifiedAt;

    @Column(name = "dmarc_verified_at")
    private Instant dmarcVerifiedAt;

    @Column(name = "ownership_verified_at")
    private Instant ownershipVerifiedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dns_report", nullable = false, columnDefinition = "jsonb")
    private String dnsReport = "{}";

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
