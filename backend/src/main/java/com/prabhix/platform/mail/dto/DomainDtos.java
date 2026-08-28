package com.prabhix.platform.mail.dto;

import com.prabhix.platform.mail.domain.MailEnums;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DomainDtos {

    private DomainDtos() {
    }

    public record CreateDomainRequest(
            @NotBlank String domain,
            MailEnums.DomainMode mode) {
    }

    public record DomainResponse(
            UUID id,
            String domain,
            MailEnums.DomainStatus status,
            MailEnums.DomainMode mode,
            boolean isDefault,
            Instant mxVerifiedAt,
            Instant spfVerifiedAt,
            Instant dkimVerifiedAt,
            Instant dmarcVerifiedAt,
            Instant ownershipVerifiedAt) {
    }

    public record DnsRecord(
            String name,
            String type,
            String expected,
            String observed,
            String status) {
    }

    public record DnsReport(UUID domainId, List<DnsRecord> records, MailEnums.DomainStatus domainStatus) {
    }
}
