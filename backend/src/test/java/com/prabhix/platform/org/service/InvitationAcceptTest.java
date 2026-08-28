package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.Invitation;
import com.prabhix.platform.org.domain.Invitation.InvitationStatus;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.dto.OrgDtos.AcceptInvitationRequest;
import com.prabhix.platform.org.repository.InvitationRepository;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationAcceptTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OrganizationService organizationService;
    @Mock private OrganizationMemberService memberService;
    @Mock private RoleService roleService;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher events;
    @Mock private EntitlementGate entitlements;

    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(null, null, null, null, null, null, null, null);
        invitationService = new InvitationService(
                invitationRepository, membershipRepository, roleRepository, organizationService,
                memberService, roleService, userService, events, properties, entitlements);
    }

    @Test
    void acceptHappyPathCreatesMembership() {
        String rawToken = "invite-token";
        UUID orgId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();

        Invitation invitation = pendingInvite(inviteId, orgId, roleId, rawToken);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("invitee@example.com");

        when(invitationRepository.findByTokenHash(InvitationService.sha256(rawToken)))
                .thenReturn(Optional.of(invitation));
        when(userService.findByEmail("invitee@example.com")).thenReturn(Optional.of(user));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID result = invitationService.accept(new AcceptInvitationRequest(rawToken, null, null));

        assertEquals(orgId, result);
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        verify(memberService).addMember(orgId, user.getId(), roleId, invitation.getInvitedBy(), user);
    }

    @Test
    void expiredInviteIsRejected() {
        String rawToken = "expired-token";
        Invitation invitation = pendingInvite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), rawToken);
        invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(invitationRepository.findByTokenHash(InvitationService.sha256(rawToken)))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApiException ex = assertThrows(ApiException.class,
                () -> invitationService.accept(new AcceptInvitationRequest(rawToken, null, null)));
        assertEquals(ErrorCode.INVALID_STATE, ex.getCode());
        verify(memberService, never()).addMember(any(), any(), any(), any(), any());
    }

    @Test
    void alreadyAcceptedInviteIsRejected() {
        String rawToken = "used-token";
        Invitation invitation = pendingInvite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), rawToken);
        invitation.setStatus(InvitationStatus.ACCEPTED);

        when(invitationRepository.findByTokenHash(InvitationService.sha256(rawToken)))
                .thenReturn(Optional.of(invitation));

        ApiException ex = assertThrows(ApiException.class,
                () -> invitationService.accept(new AcceptInvitationRequest(rawToken, null, null)));
        assertEquals(ErrorCode.CONFLICT, ex.getCode());
    }

    private Invitation pendingInvite(UUID id, UUID orgId, UUID roleId, String rawToken) {
        Invitation invitation = new Invitation();
        invitation.setId(id);
        invitation.setOrganizationId(orgId);
        invitation.setRoleId(roleId);
        invitation.setEmail("invitee@example.com");
        invitation.setTokenHash(InvitationService.sha256(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setInvitedBy(UUID.randomUUID());
        return invitation;
    }
}
