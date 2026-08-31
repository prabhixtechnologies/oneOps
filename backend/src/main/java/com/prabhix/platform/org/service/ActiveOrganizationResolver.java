package com.prabhix.platform.org.service;

import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which tenant a request belongs to when nothing has named one.
 *
 * <p>Extracted so there is exactly one answer. Sign-in worked this out from the user's default
 * organization; the identity-token path needed the same answer and would otherwise have grown its
 * own copy. Two rules for "which tenant are you in" that drift apart is the kind of difference that
 * shows up as one product disagreeing with another about what somebody can see.
 *
 * <p>An explicit {@code X-Prabhix-Org} header always wins over this, and is validated against
 * membership by the caller. This only fills the gap before anything has chosen.
 */
@Service
@RequiredArgsConstructor
public class ActiveOrganizationResolver {

    private final OrganizationMembershipRepository memberships;

    /**
     * @param defaultOrgId the user's remembered choice, honoured only if still an active membership —
     *     a membership can be suspended or removed long after it was made the default.
     * @return the organization to scope this request to, or null when it is genuinely ambiguous:
     *     several active memberships and no usable default. Null is not a failure. It means "ask
     *     them", and the caller resolves no tenant-scoped permissions until they have.
     */
    @Transactional(readOnly = true)
    public UUID resolve(UUID userId, UUID defaultOrgId) {
        if (defaultOrgId != null) {
            Optional<OrganizationMembership> membership =
                    memberships.findByOrganizationIdAndUserId(defaultOrgId, userId);
            if (membership.isPresent() && membership.get().getStatus() == MembershipStatus.ACTIVE) {
                return defaultOrgId;
            }
        }
        // Exactly one, not the first of several. Picking one arbitrarily out of several would put
        // somebody in a tenant they did not choose, and the choice would look like the product's
        // opinion rather than an accident.
        List<OrganizationMembership> active =
                memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        return active.size() == 1 ? active.get(0).getOrganizationId() : null;
    }
}
