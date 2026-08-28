package com.prabhix.platform.org.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OrgDtos {

    private OrgDtos() {
    }

    public record OrganizationView(
            UUID id,
            String name,
            String slug,
            String status,
            int memberCount,
            int seatLimit,
            Instant trialEndsAt,
            String timezone,
            String locale,
            String currency,
            Instant createdAt) {
    }

    public record CreateOrganizationRequest(
            @NotBlank @Size(max = 200) String name) {
    }

    public record UpdateOrganizationRequest(
            @Size(max = 200) String name,
            @Size(max = 250) String legalName,
            @Size(max = 15) String gstin,
            @Size(max = 10) String pan,
            @Email String billingEmail,
            @Size(max = 32) String phone,
            @Size(max = 255) String website,
            @Size(max = 64) String timezone,
            @Size(max = 16) String locale) {
    }

    public record MemberView(
            UUID id,
            UUID userId,
            String displayName,
            String email,
            UUID roleId,
            String roleName,
            String status,
            String department,
            String employeeId,
            Instant joinedAt,
            Instant lastActiveAt) {
    }

    public record MemberListQuery(
            String cursor,
            Integer limit,
            String search,
            String status,
            UUID roleId,
            String department) {
    }

    public record ChangeMemberRoleRequest(
            @NotNull UUID roleId) {
    }

    public record RoleView(
            UUID id,
            String roleKey,
            String name,
            String description,
            boolean system,
            int rank,
            Set<String> permissions) {
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = 64) String roleKey,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 255) String description,
            @NotEmpty Set<String> permissions) {
    }

    public record UpdateRoleRequest(
            @Size(max = 80) String name,
            @Size(max = 255) String description,
            Set<String> permissions) {
    }

    public record TeamView(
            UUID id,
            String slug,
            String name,
            String description,
            UUID leadUserId,
            int memberCount) {
    }

    public record CreateTeamRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            UUID leadUserId) {
    }

    public record UpdateTeamRequest(
            @Size(max = 120) String name,
            @Size(max = 500) String description,
            UUID leadUserId) {
    }

    public record TeamMemberView(
            UUID id,
            UUID userId,
            String displayName,
            String teamRole) {
    }

    public record AddTeamMemberRequest(
            @NotNull UUID userId,
            String teamRole) {
    }

    public record CreateInvitationRequest(
            @NotBlank @Email String email,
            @NotNull UUID roleId,
            UUID teamId,
            @Size(max = 1000) String message) {
    }

    public record InvitationPreview(
            String organizationName,
            String roleName) {
    }

    public record AcceptInvitationRequest(
            @NotBlank String token,
            @Size(max = 160) String fullName,
            @Size(min = 10, max = 128) String password) {
    }

    public record InvitationView(
            UUID id,
            String email,
            UUID roleId,
            String roleName,
            String invitedBy,
            Instant expiresAt,
            Instant createdAt) {
    }

    public record ApiKeyView(
            UUID id,
            String name,
            String prefix,
            Instant lastUsedAt,
            Instant createdAt,
            Instant expiresAt) {
    }

    public record CreatedApiKeyView(
            UUID id,
            String name,
            String prefix,
            String key,
            Instant lastUsedAt,
            Instant createdAt,
            Instant expiresAt) {
    }

    public record CreateApiKeyRequest(
            @NotBlank @Size(max = 120) String name,
            Instant expiresAt,
            /** When null, the key inherits every permission the creator holds today. */
            java.util.Set<String> scopes) {
    }

    public record PermissionCategory(
            String category,
            List<PermissionEntry> permissions) {
    }

    public record PermissionEntry(
            String code,
            String description) {
    }
}
