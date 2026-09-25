package com.prabhix.platform.auth.web;

import com.prabhix.identity.client.BearerTokens;
import com.prabhix.platform.auth.dto.AuthDtos.AuthMeResponse;
import com.prabhix.platform.auth.service.AuthService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two {@code /auth} paths the consoles still ask this service, rather than identity.
 *
 * <p>Everything that establishes a session — sign-in by any means, sign-up, refresh, the browser
 * session cookie, password recovery, email verification — lives at identity now, and the edge routes
 * the rest of {@code /api/v1/oneops/auth/*} there. These two stay because only this database can answer
 * them: which organization the caller is acting in and what they may do there, and the revocation
 * that makes a sign-out take effect here before the token expires.
 */
@RestController
@RequestMapping("/api/v1/oneops/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/me")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public AuthMeResponse me(@CurrentUser PrabhixPrincipal principal) {
        return authService.currentUser(principal);
    }

    @PostMapping("/logout")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void logout(@CurrentUser PrabhixPrincipal principal, HttpServletRequest request) {
        authService.logout(principal, BearerTokens.from(request));
    }
}
