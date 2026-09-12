package com.prabhix.platform.auth.web;

import com.prabhix.platform.auth.LegacyCredentialLoginGuard;
import com.prabhix.platform.auth.dto.AuthDtos.AckResponse;
import com.prabhix.platform.auth.dto.AuthDtos.AuthMeResponse;
import com.prabhix.platform.auth.dto.AuthDtos.EmailVerifyConfirmRequest;
import com.prabhix.platform.auth.dto.AuthDtos.EmailRequest;
import com.prabhix.platform.auth.dto.AuthDtos.GoogleSsoRequest;
import com.prabhix.platform.auth.dto.AuthDtos.LoginRequest;
import com.prabhix.platform.auth.dto.AuthDtos.LogoutRequest;
import com.prabhix.platform.auth.dto.AuthDtos.MagicLinkVerifyRequest;
import com.prabhix.platform.auth.dto.AuthDtos.OtpVerifyRequest;
import com.prabhix.platform.auth.dto.AuthDtos.PasswordResetRequest;
import com.prabhix.platform.auth.dto.AuthDtos.RefreshRequest;
import com.prabhix.platform.auth.dto.AuthDtos.RegisterRequest;
import com.prabhix.platform.auth.dto.AuthDtos.TokenResponse;
import com.prabhix.platform.auth.service.AuthService;
import com.prabhix.platform.auth.service.EmailVerificationService;
import com.prabhix.platform.auth.service.GoogleSsoService;
import com.prabhix.platform.auth.service.PasswordlessAuthService;
import com.prabhix.platform.auth.service.SessionCookieService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordlessAuthService passwordlessAuthService;
    private final EmailVerificationService emailVerificationService;
    private final GoogleSsoService googleSsoService;
    private final SessionCookieService sessionCookieService;
    private final LegacyCredentialLoginGuard legacyCredentialLoginGuard;

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request,
                                  HttpServletResponse response) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return withSessionCookie(authService.register(request), response);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest http,
                               HttpServletResponse response) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return withSessionCookie(
                authService.login(request, http.getRemoteAddr(), http.getHeader("User-Agent")),
                response);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 HttpServletResponse response) {
        return withSessionCookie(authService.refresh(request.refreshToken()), response);
    }

    /**
     * Exchanges the shared browser session cookie for a short-lived access token.
     *
     * <p>This is how a browser stays signed in now, in place of a refresh token held in
     * {@code localStorage}. It is unauthenticated in the bearer-token sense — the cookie *is* the
     * credential — and it neither rotates nor consumes anything, so every open tab across every
     * console hostname can call it whenever its access token is about to expire without the calls
     * interfering with each other.
     *
     * <p>Native clients do not use this: they have no cookie jar, and keep refreshing with the token
     * returned in the login response.
     */
    @PostMapping("/session/token")
    public TokenResponse sessionToken(HttpServletRequest http, HttpServletResponse response) {
        return sessionCookieService.exchange(http, response);
    }

    @PostMapping("/logout")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void logout(@CurrentUser PrabhixPrincipal principal,
                       @RequestBody(required = false) LogoutRequest request,
                       HttpServletResponse response) {
        String refresh = request != null ? request.refreshToken() : null;
        authService.logout(principal.userId(), principal.sessionId(), refresh);
        // Without this the browser keeps sending a cookie whose session is revoked, so every page
        // load spends a request discovering it is signed out.
        sessionCookieService.clear(response);
    }

    @GetMapping("/me")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public AuthMeResponse me(@CurrentUser PrabhixPrincipal principal) {
        return authService.currentUser(principal);
    }

    @PostMapping("/magic-link/request")
    public AckResponse requestMagicLink(@Valid @RequestBody EmailRequest request,
                                        HttpServletRequest http) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return passwordlessAuthService.requestMagicLink(request, http.getRemoteAddr());
    }

    @PostMapping("/magic-link/verify")
    public TokenResponse verifyMagicLink(@Valid @RequestBody MagicLinkVerifyRequest request,
                                         HttpServletResponse response) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return withSessionCookie(passwordlessAuthService.verifyMagicLink(request), response);
    }

    @PostMapping("/otp/request")
    public AckResponse requestOtp(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return passwordlessAuthService.requestOtp(request, http.getRemoteAddr());
    }

    @PostMapping("/otp/verify")
    public TokenResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                   HttpServletResponse response) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return withSessionCookie(passwordlessAuthService.verifyOtp(request), response);
    }

    @PostMapping("/password/forgot")
    public AckResponse forgotPassword(@Valid @RequestBody EmailRequest request,
                                      HttpServletRequest http) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return passwordlessAuthService.requestPasswordReset(request, http.getRemoteAddr());
    }

    @PostMapping("/password/reset")
    public AckResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return passwordlessAuthService.resetPassword(request);
    }

    @PostMapping("/email/verify/request")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public AckResponse requestEmailVerification(@CurrentUser PrabhixPrincipal principal,
                                                HttpServletRequest http) {
        return emailVerificationService.requestVerification(principal.userId(), http.getRemoteAddr());
    }

    @PostMapping("/email/verify/confirm")
    public AckResponse confirmEmailVerification(@Valid @RequestBody EmailVerifyConfirmRequest request) {
        return emailVerificationService.confirmVerification(request);
    }

    @PostMapping("/sso/google")
    public TokenResponse googleSso(@Valid @RequestBody GoogleSsoRequest request,
                                   HttpServletRequest http,
                                   HttpServletResponse response) {
        legacyCredentialLoginGuard.requireLegacyEnabled();
        return withSessionCookie(
                googleSsoService.authenticate(request, http.getRemoteAddr(), http.getHeader("User-Agent")),
                response);
    }

    /**
     * Attaches the shared session cookie to a response that already carries tokens.
     *
     * <p>Applied to every flow that establishes a session, so it does not matter which one a person
     * used — password, magic link, one-time code or Google — the other console hostname recognises
     * them without a second sign-in.
     *
     * <p>Native clients get the cookie too and simply ignore it; they authenticate with the bearer
     * token and refresh token in the body, which no flow has stopped returning.
     */
    private TokenResponse withSessionCookie(TokenResponse tokens, HttpServletResponse response) {
        sessionCookieService.issue(response, tokens.sessionId());
        return tokens;
    }
}
