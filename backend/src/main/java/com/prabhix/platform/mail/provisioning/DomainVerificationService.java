package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailDomain;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.dto.DomainDtos;
import com.prabhix.platform.mail.repository.MailDomainRepository;
import com.prabhix.platform.mail.util.MailJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.Instant;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainVerificationService {

    private final MailDomainRepository domainRepository;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    @Scheduled(cron = "${prabhix.mail.domain-verification.recheck-cron}")
    @Transactional
    public void recheckVerifiedDomains() {
        if (!properties.mail().domainVerification().recheckEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.mail().domainVerification().recheckInterval());
        int batchSize = properties.mail().domainVerification().recheckBatchSize();
        List<MailDomain> batch;
        do {
            batch = domainRepository.claimDueForRecheck(cutoff, batchSize);
            for (MailDomain domain : batch) {
                verifyDomain(domain);
            }
        } while (batch.size() == batchSize);
    }

    @Transactional
    public DomainDtos.DnsReport verify(UUID domainId, UUID organizationId) {
        MailDomain domain = domainRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(domainId, organizationId)
                .orElseThrow();
        return verifyDomain(domain);
    }

    private DomainDtos.DnsReport verifyDomain(MailDomain domain) {
        MailEnums.DomainStatus previousStatus = domain.getStatus();
        List<DomainDtos.DnsRecord> records = DnsRecordBuilder.build(
                domain.getDomain(), domain.getDkimSelector(),
                domain.getDkimPublicKey(), domain.getVerificationToken());

        List<DomainDtos.DnsRecord> verified = new java.util.ArrayList<>();
        for (DomainDtos.DnsRecord record : records) {
            String observed = lookup(record.name(), record.type());
            String status = matchRecord(record, observed) ? "VERIFIED" : "FAILED";
            DomainDtos.DnsRecord updated = new DomainDtos.DnsRecord(
                    record.name(), record.type(), record.expected(), observed, status);
            updateVerifiedAt(domain, updated);
            verified.add(updated);
        }

        applyDomainStatus(domain, previousStatus);
        domain.setDnsReport(MailJson.toJson(verified));
        domain.setLastCheckedAt(Instant.now());
        domainRepository.save(domain);

        return new DomainDtos.DnsReport(domain.getId(), verified, domain.getStatus());
    }

    @Transactional(readOnly = true)
    public DomainDtos.DnsReport getDnsReport(UUID domainId, UUID organizationId) {
        MailDomain domain = domainRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(domainId, organizationId)
                .orElseThrow();
        List<DomainDtos.DnsRecord> records = MailJson.parseObjectList(domain.getDnsReport()).stream()
                .map(m -> new DomainDtos.DnsRecord(
                        String.valueOf(m.get("name")),
                        String.valueOf(m.get("type")),
                        String.valueOf(m.get("expected")),
                        m.get("observed") != null ? String.valueOf(m.get("observed")) : null,
                        String.valueOf(m.get("status"))))
                .toList();
        if (records.isEmpty()) {
            records = DnsRecordBuilder.build(domain.getDomain(), domain.getDkimSelector(),
                    domain.getDkimPublicKey(), domain.getVerificationToken());
        }
        return new DomainDtos.DnsReport(domain.getId(), records, domain.getStatus());
    }

    private boolean matchRecord(DomainDtos.DnsRecord record, String observed) {
        if (observed == null) {
            return false;
        }
        return observed.contains(record.expected()) || record.expected().contains(observed.trim());
    }

    private void applyDomainStatus(MailDomain domain, MailEnums.DomainStatus previousStatus) {
        if (domain.getOwnershipVerifiedAt() != null && domain.getMxVerifiedAt() != null) {
            domain.setStatus(MailEnums.DomainStatus.VERIFIED);
            return;
        }
        if (previousStatus == MailEnums.DomainStatus.VERIFIED) {
            domain.setStatus(MailEnums.DomainStatus.FAILED);
            domain.setMxVerifiedAt(null);
            domain.setSpfVerifiedAt(null);
            domain.setDkimVerifiedAt(null);
            domain.setDmarcVerifiedAt(null);
            domain.setOwnershipVerifiedAt(null);
            events.publishEvent(AuditRequested.changed(
                    domain.getOrganizationId(), null,
                    "mail.domain.regressed", "mail_domain", domain.getId(),
                    Map.of("status", Map.of("from", "VERIFIED", "to", "FAILED"),
                            "domain", domain.getDomain())));
        }
    }

    private void updateVerifiedAt(MailDomain domain, DomainDtos.DnsRecord record) {
        Instant now = "VERIFIED".equals(record.status()) ? Instant.now() : null;
        switch (record.type()) {
            case "MX" -> domain.setMxVerifiedAt(now);
            case "TXT" -> {
                if (record.expected().startsWith("v=spf1")) {
                    domain.setSpfVerifiedAt(now);
                } else if (record.expected().startsWith("v=DKIM1")) {
                    domain.setDkimVerifiedAt(now);
                } else if (record.expected().startsWith("v=DMARC1")) {
                    domain.setDmarcVerifiedAt(now);
                } else if (record.expected().startsWith("prabhix-verification=")) {
                    domain.setOwnershipVerifiedAt(now);
                }
            }
            default -> { }
        }
    }

    String lookup(String name, String type) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(name, new String[]{type});
            Attribute attr = attrs.get(type);
            if (attr == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < attr.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(attr.get(i));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.debug("DNS lookup failed for {} {}: {}", type, name, ex.getMessage());
            return null;
        }
    }
}
