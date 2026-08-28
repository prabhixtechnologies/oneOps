package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.domain.Team;
import com.prabhix.platform.org.domain.TeamMember;
import com.prabhix.platform.org.domain.TeamMember.TeamRole;
import com.prabhix.platform.org.dto.OrgDtos.AddTeamMemberRequest;
import com.prabhix.platform.org.dto.OrgDtos.CreateTeamRequest;
import com.prabhix.platform.org.dto.OrgDtos.TeamMemberView;
import com.prabhix.platform.org.dto.OrgDtos.TeamView;
import com.prabhix.platform.org.dto.OrgDtos.UpdateTeamRequest;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.TeamMemberRepository;
import com.prabhix.platform.org.repository.TeamRepository;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<TeamView> listTeams(UUID organizationId) {
        return PageResponse.of(teamRepository.findByOrganizationIdOrderByNameAsc(organizationId)
                .stream().map(this::toView).toList());
    }

    @Transactional
    public TeamView createTeam(UUID organizationId, CreateTeamRequest request) {
        String slug = uniquifySlug(organizationId, Ids.slug(request.name()));
        Team team = new Team();
        team.setOrganizationId(organizationId);
        team.setName(request.name().trim());
        team.setSlug(slug);
        team.setDescription(request.description());
        team.setLeadUserId(request.leadUserId());
        team.setMemberCount(0);
        return toView(teamRepository.save(team));
    }

    @Transactional
    public TeamView updateTeam(UUID organizationId, UUID teamId, UpdateTeamRequest request) {
        Team team = requireTeam(organizationId, teamId);
        if (request.name() != null && !request.name().isBlank()) {
            team.setName(request.name().trim());
        }
        if (request.description() != null) {
            team.setDescription(request.description().isBlank() ? null : request.description().trim());
        }
        if (request.leadUserId() != null) {
            team.setLeadUserId(request.leadUserId());
        }
        return toView(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(UUID organizationId, UUID teamId) {
        Team team = requireTeam(organizationId, teamId);
        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamMemberView> listTeamMembers(UUID organizationId, UUID teamId) {
        requireTeam(organizationId, teamId);
        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        Map<UUID, User> usersById = userRepository.findAllById(
                members.stream().map(TeamMember::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<TeamMemberView> views = members.stream()
                .map(member -> toMemberView(member, usersById.get(member.getUserId())))
                .toList();
        return PageResponse.of(views);
    }

    @Transactional
    public TeamMemberView addMember(UUID organizationId, UUID teamId, AddTeamMemberRequest request) {
        requireTeam(organizationId, teamId);
        membershipRepository.findByOrganizationIdAndUserId(organizationId, request.userId())
                .orElseThrow(() -> ApiException.notFound("Member"));

        if (teamMemberRepository.findByTeamIdAndUserId(teamId, request.userId()).isPresent()) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.ALREADY_EXISTS,
                    "That user is already on this team");
        }

        TeamMember member = new TeamMember();
        member.setOrganizationId(organizationId);
        member.setTeamId(teamId);
        member.setUserId(request.userId());
        member.setTeamRole(parseTeamRole(request.teamRole()));
        member = teamMemberRepository.save(member);

        adjustMemberCount(teamId, 1);
        User user = userRepository.findById(request.userId()).orElse(null);
        return toMemberView(member, user);
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID teamId, UUID userId) {
        requireTeam(organizationId, teamId);
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> ApiException.notFound("Team member"));
        teamMemberRepository.delete(member);
        adjustMemberCount(teamId, -1);
    }

    private Team requireTeam(UUID organizationId, UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Team"));
        if (!team.getOrganizationId().equals(organizationId)) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.CROSS_TENANT_ACCESS,
                    "That team does not belong to this organization");
        }
        return team;
    }

    private void adjustMemberCount(UUID teamId, int delta) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Team"));
        team.setMemberCount(Math.max(0, team.getMemberCount() + delta));
        teamRepository.save(team);
    }

    private String uniquifySlug(UUID organizationId, String base) {
        String candidate = base.isBlank() ? "team" : base;
        String slug = candidate;
        int suffix = 1;
        while (teamRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
            slug = candidate + "-" + suffix++;
        }
        return slug;
    }

    private TeamRole parseTeamRole(String role) {
        if (role == null || role.isBlank()) {
            return TeamRole.MEMBER;
        }
        return TeamRole.valueOf(role.trim().toUpperCase());
    }

    private TeamView toView(Team team) {
        return new TeamView(
                team.getId(),
                team.getSlug(),
                team.getName(),
                team.getDescription(),
                team.getLeadUserId(),
                team.getMemberCount());
    }

    private TeamMemberView toMemberView(TeamMember member, User user) {
        String displayName = user == null ? null : user.effectiveDisplayName();
        return new TeamMemberView(
                member.getId(),
                member.getUserId(),
                displayName,
                member.getTeamRole().name());
    }
}
