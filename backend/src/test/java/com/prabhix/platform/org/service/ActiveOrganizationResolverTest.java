package com.prabhix.platform.org.service;

import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiveOrganizationResolverTest {

    @Mock private OrganizationMembershipRepository memberships;

    private ActiveOrganizationResolver resolver;

    private final UUID userId = UUID.randomUUID();
    private final UUID defaultOrg = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new ActiveOrganizationResolver(memberships);
    }

    @Test
    void honoursTheRememberedDefault() {
        when(memberships.findByOrganizationIdAndUserId(defaultOrg, userId))
                .thenReturn(Optional.of(membership(defaultOrg, MembershipStatus.ACTIVE)));

        assertEquals(defaultOrg, resolver.resolve(userId, defaultOrg));
    }

    /**
     * The reason the status is checked rather than the row's existence: a membership can be suspended
     * or revoked long after it was made somebody's default, and the stale default would otherwise
     * still scope every request they make.
     */
    @Test
    void ignoresADefaultWhoseMembershipIsNoLongerActive() {
        UUID other = UUID.randomUUID();
        when(memberships.findByOrganizationIdAndUserId(defaultOrg, userId))
                .thenReturn(Optional.of(membership(defaultOrg, MembershipStatus.SUSPENDED)));
        when(memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership(other, MembershipStatus.ACTIVE)));

        assertEquals(other, resolver.resolve(userId, defaultOrg));
    }

    @Test
    void fallsBackToTheOnlyActiveMembership() {
        UUID only = UUID.randomUUID();
        when(memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership(only, MembershipStatus.ACTIVE)));

        assertEquals(only, resolver.resolve(userId, null));
    }

    /**
     * Null rather than the first of the list. Choosing for somebody would put them inside a tenant
     * they never picked, and it would read as the product's opinion rather than the accident it is.
     */
    @Test
    void refusesToGuessBetweenSeveral() {
        when(memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(
                        membership(UUID.randomUUID(), MembershipStatus.ACTIVE),
                        membership(UUID.randomUUID(), MembershipStatus.ACTIVE)));

        assertNull(resolver.resolve(userId, null));
    }

    @Test
    void resolvesNothingForSomebodyWithNoMemberships() {
        when(memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)).thenReturn(List.of());

        assertNull(resolver.resolve(userId, null));
    }

    private OrganizationMembership membership(UUID organizationId, MembershipStatus status) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setId(UUID.randomUUID());
        membership.setOrganizationId(organizationId);
        membership.setUserId(userId);
        membership.setStatus(status);
        return membership;
    }
}
