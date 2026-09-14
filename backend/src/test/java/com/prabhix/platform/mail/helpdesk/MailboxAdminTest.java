package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.domain.MailboxMember;
import com.prabhix.platform.mail.dto.MailboxDtos;
import com.prabhix.platform.mail.provisioning.MailboxCredentialsCipher;
import com.prabhix.platform.mail.repository.MailRoutingRuleRepository;
import com.prabhix.platform.mail.repository.MailboxMemberRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.Team;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.TeamRepository;
import com.prabhix.platform.support.TestProperties;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mailbox administration: team grants, member roles and SLA targets.
 *
 * <p>All three were schema features with no way to reach them. {@code team_id} was stored and read by
 * access resolution but could not be written; {@code access_level} was write-once and never returned;
 * and the SLA columns were surfaced as a string called {@code slaPolicyId} with no write path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailboxAdminTest {

    @Mock private MailboxRepository mailboxRepository;
    @Mock private MailboxMemberRepository memberRepository;
    @Mock private MailRoutingRuleRepository routingRuleRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private com.prabhix.platform.mail.repository.MailDomainRepository mailDomainRepository;
    @Mock private EntitlementGate entitlements;
    @Mock private MailboxCredentialsCipher credentialsCipher;

    private MailboxService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID mailboxId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MailboxService(mailboxRepository, memberRepository, routingRuleRepository,
                membershipRepository, teamRepository, mailDomainRepository,
                TestProperties.defaults(), entitlements, credentialsCipher);
        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, orgId))
                .thenReturn(Optional.of(mailbox()));
        when(memberRepository.save(any())).thenAnswer(inv -> {
            MailboxMember m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(UUID.randomUUID());
            }
            return m;
        });
    }

    @Test
    void grantsAMailboxToATeam() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team("Billing")));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team("Billing")));

        var view = service.addMember(orgId, mailboxId, new MailboxDtos.AddMailboxMemberRequest(
                null, teamId, MailEnums.MemberAccessLevel.LEAD));

        assertEquals(teamId, view.teamId());
        assertNull(view.userId());
        assertEquals("Billing", view.name());
        assertEquals(MailEnums.MemberAccessLevel.LEAD, view.accessLevel());
    }

    /** A team from another tenant must not be nameable, even by someone who knows its id. */
    @Test
    void refusesATeamFromAnotherOrganization() {
        Team foreign = team("Someone else's team");
        foreign.setOrganizationId(UUID.randomUUID());
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(foreign));

        ApiException ex = assertThrows(ApiException.class, () -> service.addMember(orgId, mailboxId,
                new MailboxDtos.AddMailboxMemberRequest(null, teamId, null)));
        assertEquals(ErrorCode.NOT_FOUND, ex.getCode());
    }

    @Test
    void refusesADuplicateTeamGrant() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team("Billing")));
        when(memberRepository.existsByMailboxIdAndTeamId(mailboxId, teamId)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.addMember(orgId, mailboxId,
                new MailboxDtos.AddMailboxMemberRequest(null, teamId, null)));
        assertEquals(ErrorCode.ALREADY_EXISTS, ex.getCode());
    }

    @Test
    void userGrantsStillWorkAndCarryTheirAccessLevel() {
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, userId))
                .thenReturn(Optional.of(membership()));
        when(membershipRepository.findByOrganizationIdAndUserIdIn(any(), any()))
                .thenReturn(List.of(membership()));

        var view = service.addMember(orgId, mailboxId, new MailboxDtos.AddMailboxMemberRequest(
                userId, null, MailEnums.MemberAccessLevel.LEAD));

        assertEquals(userId, view.userId());
        assertNull(view.teamId());
        assertEquals("priya@acme.com", view.email());
        assertEquals(MailEnums.MemberAccessLevel.LEAD, view.accessLevel());
    }

    @Test
    void promotesAMemberToLead() {
        MailboxMember member = new MailboxMember();
        member.setId(UUID.randomUUID());
        member.setMailboxId(mailboxId);
        member.setOrganizationId(orgId);
        member.setUserId(userId);
        member.setAccessLevel(MailEnums.MemberAccessLevel.MEMBER);
        when(memberRepository.findByIdAndMailboxIdAndOrganizationId(member.getId(), mailboxId, orgId))
                .thenReturn(Optional.of(member));
        when(membershipRepository.findByOrganizationIdAndUserIdIn(any(), any()))
                .thenReturn(List.of(membership()));

        var view = service.updateMember(orgId, mailboxId, member.getId(),
                new MailboxDtos.UpdateMailboxMemberRequest(MailEnums.MemberAccessLevel.LEAD));

        assertEquals(MailEnums.MemberAccessLevel.LEAD, view.accessLevel());
        assertEquals(MailEnums.MemberAccessLevel.LEAD, member.getAccessLevel());
    }

    /**
     * Both grant kinds are listed. The mapper used to filter to rows with a user id, so a mailbox
     * shared with a team reported no members and read as misconfigured.
     */
    @Test
    void detailListsBothUserAndTeamGrants() {
        MailboxMember userGrant = new MailboxMember();
        userGrant.setId(UUID.randomUUID());
        userGrant.setUserId(userId);
        MailboxMember teamGrant = new MailboxMember();
        teamGrant.setId(UUID.randomUUID());
        teamGrant.setTeamId(teamId);

        when(memberRepository.findByMailboxId(mailboxId)).thenReturn(List.of(userGrant, teamGrant));
        when(membershipRepository.findByOrganizationIdAndUserIdIn(any(), any()))
                .thenReturn(List.of(membership()));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team("Billing")));
        when(routingRuleRepository.findByOrganizationIdAndMailboxIdOrderByPriorityAsc(orgId, mailboxId))
                .thenReturn(List.of());

        var detail = service.get(orgId, mailboxId);

        assertEquals(2, detail.members().size());
        assertEquals(2, detail.memberCount());
        assertTrue(detail.members().stream().anyMatch(m -> teamId.equals(m.teamId())));
        assertTrue(detail.members().stream().anyMatch(m -> userId.equals(m.userId())));
    }

    @Test
    void slaTargetsAreWritableAndZeroClearsThem() {
        Mailbox mailbox = mailbox();
        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, orgId))
                .thenReturn(Optional.of(mailbox));
        when(mailboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByMailboxId(mailboxId)).thenReturn(List.of());
        when(routingRuleRepository.findByOrganizationIdAndMailboxIdOrderByPriorityAsc(orgId, mailboxId))
                .thenReturn(List.of());

        var set = service.update(orgId, mailboxId, update(30, 480));
        assertEquals(30, set.slaFirstResponseMins());
        assertEquals(480, set.slaResolutionMins());

        // Null leaves them alone, which is what a PATCH means everywhere else in this request.
        var untouched = service.update(orgId, mailboxId, update(null, null));
        assertEquals(30, untouched.slaFirstResponseMins());
        assertEquals(480, untouched.slaResolutionMins());

        var cleared = service.update(orgId, mailboxId, update(0, 0));
        assertNull(cleared.slaFirstResponseMins());
        assertNull(cleared.slaResolutionMins());
    }

    private MailboxDtos.UpdateMailboxRequest update(Integer firstResponse, Integer resolution) {
        return new MailboxDtos.UpdateMailboxRequest(
                null, null, null, null, firstResponse, resolution, null, null);
    }

    private Mailbox mailbox() {
        Mailbox mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setOrganizationId(orgId);
        mailbox.setAddress("support@acme.com");
        mailbox.setName("Support");
        return mailbox;
    }

    private Team team(String name) {
        Team team = new Team();
        team.setId(teamId);
        team.setOrganizationId(orgId);
        team.setName(name);
        return team;
    }

    private OrganizationMembership membership() {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganizationId(orgId);
        membership.setUserId(userId);
        membership.setDisplayName("Priya");
        membership.setEmail("priya@acme.com");
        return membership;
    }
}
