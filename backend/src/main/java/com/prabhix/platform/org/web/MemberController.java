package com.prabhix.platform.org.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.org.dto.OrgDtos.ChangeMemberRoleRequest;
import com.prabhix.platform.org.dto.OrgDtos.MemberListQuery;
import com.prabhix.platform.org.dto.OrgDtos.MemberView;
import com.prabhix.platform.org.service.OrganizationMemberService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final OrganizationMemberService memberService;

    @GetMapping
    @PreAuthorize(Authorize.ORG_MEMBER_READ)
    public CursorPage<MemberView> list(@PathVariable UUID orgId,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestParam(required = false) String search,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) UUID roleId,
                                       @RequestParam(required = false) String department) {
        return memberService.listMembers(orgId, new MemberListQuery(
                cursor, limit, search, status, roleId, department));
    }

    @PatchMapping("/{memberId}/role")
    @PreAuthorize(Authorize.ORG_MEMBER_UPDATE)
    public MemberView changeRole(@PathVariable UUID orgId,
                                 @PathVariable UUID memberId,
                                 @CurrentUser PrabhixPrincipal principal,
                                 @Valid @RequestBody ChangeMemberRoleRequest request) {
        return memberService.changeRole(orgId, memberId, request, principal.userId());
    }

    @PatchMapping("/{memberId}/suspend")
    @PreAuthorize(Authorize.ORG_MEMBER_UPDATE)
    public void suspend(@PathVariable UUID orgId,
                        @PathVariable UUID memberId,
                        @CurrentUser PrabhixPrincipal principal) {
        memberService.suspend(orgId, memberId, principal.userId());
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize(Authorize.ORG_MEMBER_REMOVE)
    public void remove(@PathVariable UUID orgId,
                       @PathVariable UUID memberId,
                       @CurrentUser PrabhixPrincipal principal) {
        memberService.remove(orgId, memberId, principal.userId(), principal.email());
    }
}
