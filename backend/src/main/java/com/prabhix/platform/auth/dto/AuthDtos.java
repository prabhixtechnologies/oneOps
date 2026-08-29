package com.prabhix.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 200) String organizationName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            String deviceId,
            String deviceName,
            String deviceType) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record LogoutRequest(
            String refreshToken) {
    }

    /**
     * @param refreshToken absent when the caller authenticated with the browser session cookie.
     *                     Native clients have no cookie jar and keep using this field; browsers no
     *                     longer store it anywhere, which is the point — a token in localStorage is
     *                     readable by any script that gets injected onto the page.
     * @param sessionId    which device session was just established. Not a credential (it is
     *                     already returned by /auth/me and listed in Settings), and needed so the
     *                     server can bind the shared session cookie to this session.
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            UUID organizationId,
            Set<String> permissions,
            UUID sessionId) {
    }

    public record AuthMeResponse(
            UUID userId,
            String email,
            String displayName,
            UUID organizationId,
            UUID sessionId,
            Set<String> permissions,
            boolean platformAdmin) {
    }

    public record EmailRequest(
            @NotBlank @Email String email) {
    }

    public record MagicLinkVerifyRequest(
            @NotBlank String token) {
    }

    public record OtpVerifyRequest(
            @NotBlank @Email String email,
            @NotBlank String code) {
    }

    public record PasswordResetRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    public record EmailVerifyConfirmRequest(
            @NotBlank String token) {
    }

    public record GoogleSsoRequest(
            @NotBlank String idToken,
            String deviceId,
            String deviceName,
            String deviceType) {
    }

    public record AckResponse(
            String message) {
    }
}
