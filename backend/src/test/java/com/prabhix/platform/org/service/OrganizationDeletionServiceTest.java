package com.prabhix.platform.org.service;

import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.billing.service.EntitlementService;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.security.rbac.SystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationDeletionServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMembershipRepository membershipRepository;
    @Mock RoleRepository roleRepository;
    @Mock BillingSubscriptionRepository subscriptionRepository;
    @Mock EntitlementService entitlementService;
    @Mock DeviceSessionRepository deviceSessionRepository;
    @Mock TokenDenyList tokenDenyList;
    @Mock PermissionResolver permissionResolver;
    @Mock ApplicationEventPublisher events;

    OrganizationDeletionService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID ownerRoleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrganizationDeletionService(
                organizationRepository, membershipRepository, roleRepository,
                subscriptionRepository, entitlementService, deviceSessionRepository,
                tokenDenyList, permissionResolver, events);
    }

    @Test
    void requiresConfirmWhenMultipleOwners() {
        stubActiveOwner();
        when(membershipRepository.countActiveByRole(orgId, ownerRoleId)).thenReturn(2L);

        assertThrows(ApiException.class, () -> service.delete(orgId, ownerId, false));
    }

    /**
     * The sole-owner case is the most dangerous, not the safest: there is no co-owner left to
     * notice or reverse an accidental delete, so it must not be the one path that skips
     * confirmation.
     */
    @Test
    void requiresConfirmEvenForTheSoleOwner() {
        stubActiveOwner();
        when(membershipRepository.countActiveByRole(orgId, ownerRoleId)).thenReturn(1L);

        assertThrows(ApiException.class, () -> service.delete(orgId, ownerId, false));

        verify(organizationRepository, never()).save(any());
        verify(tokenDenyList, never()).revokeUser(any());
    }

    private void stubActiveOwner() {
        Organization org = new Organization();
        org.setId(orgId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        when(membershipRepository.findByOrganizationIdAndUserId(orgId, ownerId))
                .thenReturn(Optional.of(activeOwnerMembership()));

        Role ownerRole = new Role();
        ownerRole.setId(ownerRoleId);
        when(roleRepository.findByOrganizationIdIsNullAndRoleKey(SystemRole.OWNER.name()))
                .thenReturn(Optional.of(ownerRole));
    }

    private OrganizationMembership activeOwnerMembership() {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganizationId(orgId);
        membership.setUserId(ownerId);
        membership.setRoleId(ownerRoleId);
        membership.setStatus(MembershipStatus.ACTIVE);
        return membership;
    }
}
