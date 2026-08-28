package com.prabhix.platform.org.web;

import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.dto.OrgDtos.AcceptInvitationRequest;
import com.prabhix.platform.org.dto.OrgDtos.CreateInvitationRequest;
import com.prabhix.platform.org.dto.OrgDtos.InvitationPreview;
import com.prabhix.platform.org.dto.OrgDtos.InvitationView;
import com.prabhix.platform.org.service.InvitationService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @GetMapping("/api/v1/invites")
    @PreAuthorize(Authorize.ORG_MEMBER_READ)
    public PageResponse<InvitationView> list(@CurrentUser PrabhixPrincipal principal) {
        return invitationService.listPending(principal.requireOrganizationId());
    }

    @PostMapping("/api/v1/invites")
    @PreAuthorize(Authorize.ORG_MEMBER_INVITE)
    public Map<String, UUID> create(@CurrentUser PrabhixPrincipal principal,
                                    @Valid @RequestBody CreateInvitationRequest request) {
        var issued = invitationService.create(
                principal.requireOrganizationId(),
                principal.userId(),
                principal.displayName(),
                request);
        return Map.of("id", issued.invitation().getId());
    }

    /** Public preview — path matches SecurityConfig allow-list. */
    @GetMapping("/api/v1/auth/invites/{token}/preview")
    public InvitationPreview preview(@PathVariable String token) {
        return invitationService.preview(token);
    }

    @PostMapping("/api/v1/invites/accept")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, UUID> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        UUID orgId = invitationService.accept(request);
        return Map.of("organizationId", orgId);
    }

    @PostMapping("/api/v1/invites/{id}/revoke")
    @PreAuthorize(Authorize.ORG_MEMBER_INVITE)
    public void revoke(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        invitationService.revoke(principal.requireOrganizationId(), id);
    }

    @PostMapping("/api/v1/invites/{id}/resend")
    @PreAuthorize(Authorize.ORG_MEMBER_INVITE)
    public Map<String, UUID> resend(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        var issued = invitationService.resend(
                principal.requireOrganizationId(), id, principal.displayName());
        return Map.of("id", issued.invitation().getId());
    }
}
