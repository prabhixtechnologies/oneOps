package com.prabhix.platform.auth.web;

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
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.servlet.http.HttpServletRequest;
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

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void logout(@CurrentUser PrabhixPrincipal principal,
                       @RequestBody(required = false) LogoutRequest request) {
        String refresh = request != null ? request.refreshToken() : null;
        authService.logout(principal.userId(), principal.sessionId(), refresh);
    }

    @GetMapping("/me")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public AuthMeResponse me(@CurrentUser PrabhixPrincipal principal) {
        return authService.currentUser(principal);
    }

    @PostMapping("/magic-link/request")
    public AckResponse requestMagicLink(@Valid @RequestBody EmailRequest request,
                                        HttpServletRequest http) {
        return passwordlessAuthService.requestMagicLink(request, http.getRemoteAddr());
    }

    @PostMapping("/magic-link/verify")
    public TokenResponse verifyMagicLink(@Valid @RequestBody MagicLinkVerifyRequest request) {
        return passwordlessAuthService.verifyMagicLink(request);
    }

    @PostMapping("/otp/request")
    public AckResponse requestOtp(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        return passwordlessAuthService.requestOtp(request, http.getRemoteAddr());
    }

    @PostMapping("/otp/verify")
    public TokenResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return passwordlessAuthService.verifyOtp(request);
    }

    @PostMapping("/password/forgot")
    public AckResponse forgotPassword(@Valid @RequestBody EmailRequest request,
                                      HttpServletRequest http) {
        return passwordlessAuthService.requestPasswordReset(request, http.getRemoteAddr());
    }

    @PostMapping("/password/reset")
    public AckResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
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
    public TokenResponse googleSso(@Valid @RequestBody GoogleSsoRequest request, HttpServletRequest http) {
        return googleSsoService.authenticate(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
    }
}
