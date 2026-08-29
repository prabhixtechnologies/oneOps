package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.domain.OrganizationDomain;
import com.prabhix.platform.org.repository.OrganizationDomainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Domain ownership decides who can end up inside an organization, so the rules about what an
 * unverified claim may do are the whole point of this class.
 */
class OrganizationDomainServiceTest {

    private static final UUID ORG = UUID.randomUUID();

    private OrganizationDomainRepository domains;
    private DnsTxtLookup dns;
    private OrganizationDomainService service;

    @BeforeEach
    void setUp() {
        domains = mock(OrganizationDomainRepository.class);
        dns = mock(DnsTxtLookup.class);
        when(domains.save(any())).thenAnswer(call -> call.getArgument(0));
        when(domains.findClaim(anyString())).thenCallRealMethod();
        when(domains.findAutoJoinTarget(anyString())).thenCallRealMethod();
        service = new OrganizationDomainService(domains, dns);
    }

    @Test
    @DisplayName("a public mail provider cannot be claimed at all")
    void publicProvidersAreRefused() {
        // Belt to go with the DNS braces. Nobody could publish a TXT record under gmail.com, so the
        // check would fail anyway — but a misconfigured resolver turns "would fail" into every Gmail
        // user auto-joined into a stranger's organization.
        assertThatThrownBy(() -> service.claim(ORG, "gmail.com"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("a URL is refused rather than parsed into a domain")
    void malformedDomainsAreRefused() {
        for (String bad : new String[]{
                "https://example.com", "example.com/path", "example", "@example.com", "exa mple.com"}) {
            assertThatThrownBy(() -> service.claim(ORG, bad))
                    .as("should refuse %s", bad)
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    @DisplayName("claiming normalizes case and a trailing dot, so one domain is not three rows")
    void domainIsNormalized() {
        when(domains.findByDomainAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        OrganizationDomain claim = service.claim(ORG, "  PRABHIX.com.  ");

        // DNS is case-insensitive and a trailing dot is the same fully-qualified name, so storing
        // them separately would let two organizations each "own" what is one domain.
        assertThat(claim.getDomain()).isEqualTo("prabhix.com");
    }

    @Test
    @DisplayName("a domain already claimed elsewhere is refused without saying by whom")
    void secondClaimIsRefused() {
        when(domains.findByDomainAndDeletedAtIsNull("prabhix.com"))
                .thenReturn(Optional.of(claim("prabhix.com", false)));

        // The message says only "already claimed". Distinguishing "yours" from "someone else's" would
        // make this endpoint a way to ask which domains, and so which companies, are customers.
        assertThatThrownBy(() -> service.claim(ORG, "prabhix.com"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already claimed")
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("verification succeeds when the TXT record carries the expected value")
    void verifiesOnMatchingRecord() {
        OrganizationDomain claim = claim("prabhix.com", false);
        when(domains.findByIdAndOrganizationIdAndDeletedAtIsNull(claim.getId(), ORG))
                .thenReturn(Optional.of(claim));
        when(dns.txt("_prabhix-verify.prabhix.com"))
                .thenReturn(List.of("v=spf1 include:x", service.recordValue(claim)));

        OrganizationDomain verified = service.verify(ORG, claim.getId());

        // Other TXT records at the same name are normal — SPF lives there too — so the check is
        // "contains ours", not "is only ours".
        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.getLastCheckError()).isNull();
    }

    @Test
    @DisplayName("a resolver outage does not clear an existing verification")
    void dnsOutageDoesNotRevoke() {
        OrganizationDomain claim = claim("prabhix.com", true);
        Instant verifiedAt = claim.getVerifiedAt();
        when(domains.findByIdAndOrganizationIdAndDeletedAtIsNull(claim.getId(), ORG))
                .thenReturn(Optional.of(claim));
        when(dns.txt(anyString()))
                .thenThrow(new DnsTxtLookup.DnsUnavailableException("timed out"));

        assertThatThrownBy(() -> service.verify(ORG, claim.getId()))
                .isInstanceOf(ApiException.class);

        // The important assertion. DNS is not reliable enough for one failed lookup to revoke access:
        // a transient outage would un-verify every domain on the platform at once, and with auto-join
        // and invite restrictions hanging off it, that locks out onboarding for everybody.
        assertThat(claim.getVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(claim.getLastCheckError()).contains("Could not reach DNS");
    }

    @Test
    @DisplayName("a missing record fails the check and says where to look")
    void missingRecordFails() {
        OrganizationDomain claim = claim("prabhix.com", false);
        when(domains.findByIdAndOrganizationIdAndDeletedAtIsNull(claim.getId(), ORG))
                .thenReturn(Optional.of(claim));
        when(dns.txt(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> service.verify(ORG, claim.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("_prabhix-verify.prabhix.com");
        assertThat(claim.isVerified()).isFalse();
    }

    @Test
    @DisplayName("auto-join cannot be turned on for an unverified domain")
    void autoJoinRequiresVerification() {
        OrganizationDomain claim = claim("prabhix.com", false);
        when(domains.findByIdAndOrganizationIdAndDeletedAtIsNull(claim.getId(), ORG))
                .thenReturn(Optional.of(claim));

        // The check the whole feature rests on. Auto-join on an unverified domain harvests anyone who
        // signs up with an address there into an organization that has no relationship with them.
        assertThatThrownBy(() -> service.setAutoJoin(ORG, claim.getId(), true, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("invitations are unrestricted until a domain is verified")
    void inviteRestrictionIsOptIn() {
        when(domains.findByOrganizationIdAndVerifiedAtIsNotNullAndDeletedAtIsNull(ORG))
                .thenReturn(List.of());

        // Every organization today has no verified domain. An unconditional restriction would break
        // all of their invitations the moment this shipped.
        service.requireInvitableAddress(ORG, "anyone@example.com");
    }

    @Test
    @DisplayName("once a domain is verified, an address outside it cannot be invited")
    void inviteRestrictionApplies() {
        when(domains.findByOrganizationIdAndVerifiedAtIsNotNullAndDeletedAtIsNull(ORG))
                .thenReturn(List.of(claim("prabhix.com", true)));

        service.requireInvitableAddress(ORG, "owner@prabhix.com");

        // The case this guards is a typo'd domain sending an invitation with real access to somebody
        // the organization has no relationship with.
        assertThatThrownBy(() -> service.requireInvitableAddress(ORG, "owner@prabhixx.com"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("prabhix.com");
    }

    @Test
    @DisplayName("a subdomain address is not covered by the parent domain")
    void subdomainsAreNotCovered() {
        OrganizationDomain claim = claim("prabhix.com", true);

        // Verifying prabhix.com proves control of that zone, and delegated subdomains may be run by
        // somebody else entirely — a hosted status page, a reseller. Treating them as covered would
        // extend the claim past what the DNS record proves.
        assertThat(claim.covers("owner@prabhix.com")).isTrue();
        assertThat(claim.covers("owner@mail.prabhix.com")).isFalse();
        assertThat(claim.covers("owner@notprabhix.com")).isFalse();
    }

    @Test
    @DisplayName("a public provider address never auto-joins, whatever is in the table")
    void autoJoinSkipsPublicProviders() {
        assertThat(service.autoJoinTarget("someone@gmail.com")).isEmpty();
        verify(domains, never())
                .findByDomainAndVerifiedAtIsNotNullAndAutoJoinEnabledTrueAndDeletedAtIsNull(
                        anyString());
    }

    @Test
    @DisplayName("releasing a domain frees it and turns auto-join off")
    void releaseDisablesAutoJoin() {
        OrganizationDomain claim = claim("prabhix.com", true);
        claim.setAutoJoinEnabled(true);
        when(domains.findByIdAndOrganizationIdAndDeletedAtIsNull(claim.getId(), ORG))
                .thenReturn(Optional.of(claim));

        service.release(ORG, claim.getId());

        // Soft-deleted, and the unique index is partial on deleted_at, so the domain is claimable
        // again. Auto-join is cleared explicitly rather than left set on a dead row, in case the row
        // is ever read without the deleted_at filter.
        assertThat(claim.getDeletedAt()).isNotNull();
        assertThat(claim.isAutoJoinEnabled()).isFalse();
    }

    private OrganizationDomain claim(String domain, boolean verified) {
        OrganizationDomain claim = new OrganizationDomain();
        claim.setId(UUID.randomUUID());
        claim.setOrganizationId(ORG);
        claim.setDomain(domain);
        claim.setVerificationToken("token-for-" + domain);
        if (verified) {
            claim.setVerifiedAt(Instant.now());
        }
        return claim;
    }
}
