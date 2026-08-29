package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.org.domain.OrganizationDomain;
import com.prabhix.platform.org.repository.OrganizationDomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Claiming a domain, proving it, and what proving it permits.
 *
 * <p>The proof is a TXT record at {@code _prabhix-verify.<domain>}. That mechanism is chosen because
 * publishing a DNS record requires the same access as receiving mail for the domain, which is the
 * authority actually being claimed — whereas an emailed link to {@code admin@} proves only that
 * somebody reads one mailbox, and plenty of domains have no such address.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDomainService {

    private static final String RECORD_PREFIX = "_prabhix-verify.";
    private static final String VALUE_PREFIX = "prabhix-domain-verification=";

    /** Labels of letters, digits and hyphens, at least two of them, ending in an alphabetic TLD. */
    private static final Pattern DOMAIN = Pattern.compile(
            "^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    /**
     * Domains nobody may claim, whatever their DNS says.
     *
     * <p>A public mail provider is not an organization's domain even if somebody there could publish
     * the record — and for these, somebody could not. Without this list the first tenant to try
     * {@code gmail.com} would fail the DNS check and the tenth would fail it too, which is fine, but a
     * misconfigured resolver or a future wildcard bug turns "fine" into every Gmail user auto-joined
     * into a stranger's organization. Cheap belt to go with the braces.
     */
    private static final Set<String> PUBLIC_PROVIDERS = Set.of(
            "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com", "msn.com",
            "yahoo.com", "yahoo.co.in", "ymail.com", "rediffmail.com", "icloud.com", "me.com",
            "aol.com", "proton.me", "protonmail.com", "zoho.com", "zohomail.in", "mail.com",
            "gmx.com", "yandex.com", "qq.com", "163.com");

    private final OrganizationDomainRepository domains;
    private final DnsTxtLookup dns;

    @Transactional(readOnly = true)
    public List<OrganizationDomain> list(UUID organizationId) {
        return domains.findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    @Transactional
    public OrganizationDomain claim(UUID organizationId, String rawDomain) {
        String domain = OrganizationDomain.normalize(rawDomain);
        if (domain == null || !DOMAIN.matcher(domain).matches()) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST,
                    "Enter a domain like example.com, without a scheme or path");
        }
        if (PUBLIC_PROVIDERS.contains(domain)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "That is a public email provider, not an organization domain");
        }

        domains.findClaim(domain).ifPresent(existing -> {
            // Deliberately the same message whether the existing claim is this organization's or
            // somebody else's. Distinguishing them would turn this endpoint into a way to ask which
            // domains are already on the platform, and by extension who our customers are.
            throw ApiException.of(ErrorCode.CONFLICT, "That domain is already claimed");
        });

        OrganizationDomain claim = new OrganizationDomain();
        claim.setOrganizationId(organizationId);
        claim.setDomain(domain);
        // Per row, so proving control of one domain reveals nothing about the token for another.
        claim.setVerificationToken(Ids.token());
        return domains.save(claim);
    }

    /** The record the admin has to publish. */
    public String recordName(OrganizationDomain claim) {
        return RECORD_PREFIX + claim.getDomain();
    }

    public String recordValue(OrganizationDomain claim) {
        return VALUE_PREFIX + claim.getVerificationToken();
    }

    /**
     * Checks the TXT record and marks the domain verified if it matches.
     *
     * <p>A resolver failure is recorded and reported, and does not clear an existing verification. DNS
     * is not reliable enough for a single failed lookup to revoke access: a transient outage would
     * un-verify every domain on the platform and, with auto-join and invite restrictions hanging off
     * it, lock out onboarding for everybody at once.
     */
    @Transactional
    public OrganizationDomain verify(UUID organizationId, UUID domainId) {
        OrganizationDomain claim = domains
                .findByIdAndOrganizationIdAndDeletedAtIsNull(domainId, organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such domain claim"));

        claim.setLastCheckedAt(Instant.now());

        List<String> published;
        try {
            published = dns.txt(recordName(claim));
        } catch (DnsTxtLookup.DnsUnavailableException ex) {
            claim.setLastCheckError("Could not reach DNS for " + claim.getDomain()
                    + ". This is usually temporary — try again in a few minutes.");
            domains.save(claim);
            throw ApiException.of(ErrorCode.INTERNAL_ERROR, claim.getLastCheckError());
        }

        String expected = recordValue(claim);
        if (!published.contains(expected)) {
            claim.setLastCheckError(published.isEmpty()
                    ? "No TXT record found at " + recordName(claim)
                        + ". DNS changes can take up to an hour to appear."
                    : "A TXT record exists at " + recordName(claim)
                        + " but does not contain the expected value.");
            domains.save(claim);
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, claim.getLastCheckError());
        }

        claim.setVerifiedAt(Instant.now());
        claim.setLastCheckError(null);
        log.info("Organization {} verified domain {}", organizationId, claim.getDomain());
        return domains.save(claim);
    }

    /**
     * Whether this organization may invite an address.
     *
     * <p>Permissive when the organization has verified no domains, which is every organization today.
     * Making the restriction unconditional would break invitations for everybody the moment this
     * shipped; making it apply once a domain is verified means opting in is an act, and the act is
     * verifying a domain.
     */
    @Transactional(readOnly = true)
    public void requireInvitableAddress(UUID organizationId, String email) {
        List<OrganizationDomain> verified =
                domains.findByOrganizationIdAndVerifiedAtIsNotNullAndDeletedAtIsNull(organizationId);
        if (verified.isEmpty()) {
            return;
        }
        if (verified.stream().noneMatch(domain -> domain.covers(email))) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "This organization only invites addresses at "
                            + verified.stream().map(OrganizationDomain::getDomain).sorted()
                                .reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }

    /**
     * The organization somebody signing up with this address should join, if any.
     *
     * <p>Returns empty rather than throwing when there is no match, because not matching is the
     * ordinary case and not an error.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<OrganizationDomain> autoJoinTarget(String email) {
        if (email == null) {
            return java.util.Optional.empty();
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return java.util.Optional.empty();
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        if (PUBLIC_PROVIDERS.contains(domain)) {
            return java.util.Optional.empty();
        }
        return domains.findAutoJoinTarget(domain);
    }

    @Transactional
    public OrganizationDomain setAutoJoin(UUID organizationId, UUID domainId,
                                          boolean enabled, UUID roleId) {
        OrganizationDomain claim = domains
                .findByIdAndOrganizationIdAndDeletedAtIsNull(domainId, organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such domain claim"));

        if (enabled && !claim.isVerified()) {
            // The check that makes the whole feature safe. Auto-join on an unverified domain is a way
            // to harvest anybody who signs up with an address there.
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "Verify the domain before turning on automatic joining");
        }

        claim.setAutoJoinEnabled(enabled);
        claim.setAutoJoinRoleId(enabled ? roleId : null);
        return domains.save(claim);
    }

    @Transactional
    public void release(UUID organizationId, UUID domainId) {
        OrganizationDomain claim = domains
                .findByIdAndOrganizationIdAndDeletedAtIsNull(domainId, organizationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such domain claim"));
        // Soft delete, and the unique index is partial on deleted_at, so the domain becomes claimable
        // again — by this organization or another. Existing members are untouched: they joined an
        // organization, not a domain.
        claim.setDeletedAt(Instant.now());
        claim.setAutoJoinEnabled(false);
        domains.save(claim);
    }
}
