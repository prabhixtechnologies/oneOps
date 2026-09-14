package com.prabhix.platform.org.web;

import com.prabhix.platform.auth.dto.AuthDtos.AuthMeResponse;
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

    /**
     * Switches the caller's default organization.
     *
     * <p>Returns the {@code /auth/me} view for the chosen organization rather than a token: identity's
     * token names no tenant, so there is nothing to reissue. The console keeps its token, remembers the
     * id for {@code X-Prabhix-Org}, and adopts the permissions returned here.
     */
    @PostMapping("/{id}/select")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public AuthMeResponse select(@CurrentUser PrabhixPrincipal principal,
                                 @PathVariable("id") UUID organizationId) {
        return authService.selectOrganization(principal, organizationId);
    }
}
