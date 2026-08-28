package com.prabhix.platform.org.web;

import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.service.AuthService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationSelectController {

    private final AuthService authService;

    @PostMapping("/{id}/select")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public TokenResponse select(@CurrentUser PrabhixPrincipal principal, @PathVariable("id") UUID organizationId) {
        return authService.selectOrganization(principal.userId(), organizationId, principal.sessionId());
    }
}
