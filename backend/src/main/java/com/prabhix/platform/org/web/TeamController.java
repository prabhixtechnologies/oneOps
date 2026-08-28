package com.prabhix.platform.org.web;

import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.dto.OrgDtos.AddTeamMemberRequest;
import com.prabhix.platform.org.dto.OrgDtos.CreateTeamRequest;
import com.prabhix.platform.org.dto.OrgDtos.TeamMemberView;
import com.prabhix.platform.org.dto.OrgDtos.TeamView;
import com.prabhix.platform.org.dto.OrgDtos.UpdateTeamRequest;
import com.prabhix.platform.org.service.TeamService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @PreAuthorize(Authorize.ORG_TEAM_READ)
    public PageResponse<TeamView> list(@CurrentUser PrabhixPrincipal principal) {
        return teamService.listTeams(principal.requireOrganizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.ORG_TEAM_MANAGE)
    public TeamView create(@CurrentUser PrabhixPrincipal principal,
                           @Valid @RequestBody CreateTeamRequest request) {
        return teamService.createTeam(principal.requireOrganizationId(), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Authorize.ORG_TEAM_MANAGE)
    public TeamView update(@CurrentUser PrabhixPrincipal principal,
                           @PathVariable UUID id,
                           @Valid @RequestBody UpdateTeamRequest request) {
        return teamService.updateTeam(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.ORG_TEAM_MANAGE)
    public void delete(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        teamService.deleteTeam(principal.requireOrganizationId(), id);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize(Authorize.ORG_TEAM_READ)
    public PageResponse<TeamMemberView> listMembers(@CurrentUser PrabhixPrincipal principal,
                                                    @PathVariable UUID id) {
        return teamService.listTeamMembers(principal.requireOrganizationId(), id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize(Authorize.ORG_TEAM_MANAGE)
    public TeamMemberView addMember(@CurrentUser PrabhixPrincipal principal,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody AddTeamMemberRequest request) {
        return teamService.addMember(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize(Authorize.ORG_TEAM_MANAGE)
    public void removeMember(@CurrentUser PrabhixPrincipal principal,
                             @PathVariable UUID id,
                             @PathVariable UUID userId) {
        teamService.removeMember(principal.requireOrganizationId(), id, userId);
    }
}
