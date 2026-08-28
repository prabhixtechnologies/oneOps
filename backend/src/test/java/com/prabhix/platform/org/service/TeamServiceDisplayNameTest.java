package com.prabhix.platform.org.service;

import com.prabhix.platform.org.domain.Team;
import com.prabhix.platform.org.domain.TeamMember;
import com.prabhix.platform.org.domain.TeamMember.TeamRole;
import com.prabhix.platform.org.dto.OrgDtos.TeamMemberView;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.TeamMemberRepository;
import com.prabhix.platform.org.repository.TeamRepository;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceDisplayNameTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private TeamService teamService;

    @Test
    void listTeamMembersResolvesDisplayNamesInBatch() {
        UUID orgId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userOne = UUID.randomUUID();
        UUID userTwo = UUID.randomUUID();

        Team team = new Team();
        team.setId(teamId);
        team.setOrganizationId(orgId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        TeamMember first = member(teamId, orgId, userOne);
        TeamMember second = member(teamId, orgId, userTwo);
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(first, second));

        User alice = user(userOne, "Alice Example");
        User bob = user(userTwo, "Bob Example");
        when(userRepository.findAllById(java.util.Set.of(userOne, userTwo)))
                .thenReturn(List.of(alice, bob));

        List<TeamMemberView> members = teamService.listTeamMembers(orgId, teamId).items();

        assertEquals("Alice Example", members.get(0).displayName());
        assertEquals("Bob Example", members.get(1).displayName());
    }

    private TeamMember member(UUID teamId, UUID orgId, UUID userId) {
        TeamMember member = new TeamMember();
        member.setId(UUID.randomUUID());
        member.setTeamId(teamId);
        member.setOrganizationId(orgId);
        member.setUserId(userId);
        member.setTeamRole(TeamRole.MEMBER);
        return member;
    }

    private User user(UUID id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(fullName.toLowerCase().replace(' ', '.') + "@example.com");
        return user;
    }
}
