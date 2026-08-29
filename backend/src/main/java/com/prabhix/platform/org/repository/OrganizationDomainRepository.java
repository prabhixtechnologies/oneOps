package com.prabhix.platform.org.repository;

import com.prabhix.platform.org.domain.OrganizationDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationDomainRepository extends JpaRepository<OrganizationDomain, UUID> {

    List<OrganizationDomain> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    Optional<OrganizationDomain> findByIdAndOrganizationIdAndDeletedAtIsNull(
            UUID id, UUID organizationId);

    /**
     * Finds the live claim on a domain, verified or not.
     *
     * <p>Used to refuse a second claim. Checking only verified rows would let two organizations both
     * hold an unverified claim and race to verify, and the loser would get a confusing failure after
     * they had already published the TXT record.
     */
    default Optional<OrganizationDomain> findClaim(String domain) {
        return findByDomainAndDeletedAtIsNull(
                com.prabhix.platform.org.domain.OrganizationDomain.normalize(domain));
    }

    /** The auto-join lookup, which runs on sign-up. Verified and enabled only. */
    default Optional<OrganizationDomain> findAutoJoinTarget(String domain) {
        return findByDomainAndVerifiedAtIsNotNullAndAutoJoinEnabledTrueAndDeletedAtIsNull(
                com.prabhix.platform.org.domain.OrganizationDomain.normalize(domain));
    }

    /** Takes an already-normalized domain. Call {@link #findClaim} instead. */
    Optional<OrganizationDomain> findByDomainAndDeletedAtIsNull(String domain);

    /** Takes an already-normalized domain. Call {@link #findAutoJoinTarget} instead. */
    Optional<OrganizationDomain>
        findByDomainAndVerifiedAtIsNotNullAndAutoJoinEnabledTrueAndDeletedAtIsNull(String domain);

    List<OrganizationDomain> findByOrganizationIdAndVerifiedAtIsNotNullAndDeletedAtIsNull(
            UUID organizationId);
}
