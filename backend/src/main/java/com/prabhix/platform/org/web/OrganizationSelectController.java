package com.prabhix.platform.org.web;

import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.service.AuthService;
import com.prabhix.platform.auth.service.SessionCookieService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.servlet.http.HttpServletResponse;
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
    private final SessionCookieService sessionCookieService;

    @PostMapping("/{id}/select")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public TokenResponse select(@CurrentUser PrabhixPrincipal principal,
                                @PathVariable("id") UUID organizationId,
                                HttpServletResponse response) {
        TokenResponse tokens =
                authService.selectOrganization(principal.userId(), organizationId, principal.sessionId());
        // Switching organizations can land on a different device session, and the cookie has to
        // follow it — otherwise the other hostname would keep exchanging its way back into the
        // organization the user just left.
        sessionCookieService.issue(response, tokens.sessionId());
        return tokens;
    }
}
