package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.common.mail.MailRequest;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.Invitation;
import com.prabhix.platform.org.domain.Invitation.InvitationStatus;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.dto.OrgDtos.AcceptInvitationRequest;
import com.prabhix.platform.org.dto.OrgDtos.CreateInvitationRequest;
import com.prabhix.platform.org.dto.OrgDtos.InvitationPreview;
import com.prabhix.platform.org.dto.OrgDtos.InvitationView;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final java.time.Duration INVITE_TTL = java.time.Duration.ofDays(7);

    private final com.prabhix.platform.org.repository.InvitationRepository invitationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final com.prabhix.platform.org.repository.RoleRepository roleRepository;
    private final OrganizationService organizationService;
    private final OrganizationMemberService memberService;
    private final RoleService roleService;
    private final OrganizationDomainService domainService;
    private final UserService userService;
    private final ApplicationEventPublisher events;
    private final MailClient mail;
    private final PrabhixProperties properties;
    private final EntitlementGate entitlements;

    public record IssuedInvite(Invitation invitation, String rawToken) {
    }

    @Transactional
    public IssuedInvite create(UUID orgId, UUID inviterId, String inviterName, CreateInvitationRequest request) {
        Organization org = organizationService.requireOrg(orgId);
        Role role = roleService.requireRole(request.roleId());
        String email = request.email().trim().toLowerCase();

        // Checked at invite time as well as accept time, so an admin learns they are out of
        // seats before a candidate is emailed a link that will fail.
        entitlements.requireMemberSeat(orgId, org.getMemberCount());

        // No-op until this organization verifies a domain, which is what makes it opt-in rather than
        // a change that breaks every existing tenant's invitations on deploy. Once one is verified,
        // an admin can no longer invite an address outside it — the case this guards is a typo'd
        // domain quietly sending an invitation with real access to a stranger.
        domainService.requireInvitableAddress(orgId, email);

        String rawToken = Ids.token();
        String tokenHash = sha256(rawToken);

        Invitation invitation = invitationRepository
                .findByOrganizationIdAndEmailAndStatus(orgId, email, InvitationStatus.PENDING)
                .orElseGet(Invitation::new);

        invitation.setOrganizationId(orgId);
        invitation.setEmail(email);
        invitation.setRoleId(role.getId());
        invitation.setTeamId(request.teamId());
        invitation.setTokenHash(tokenHash);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setMessage(request.message());
        invitation.setExpiresAt(Instant.now().plus(INVITE_TTL));
        invitation.setInvitedBy(inviterId);
        invitation.setRevokedAt(null);
        invitation.setAcceptedAt(null);
        invitation.setAcceptedBy(null);
        invitation.setLastSentAt(Instant.now());
        invitation = invitationRepository.save(invitation);

        sendInviteEmail(org, inviterName, role, email, rawToken, request.message(), invitation.getExpiresAt());
        return new IssuedInvite(invitation, rawToken);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationView> listPending(UUID orgId) {
        List<Invitation> pending = invitationRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, InvitationStatus.PENDING);

        Set<UUID> roleIds = pending.stream().map(Invitation::getRoleId).collect(Collectors.toSet());
        Set<UUID> inviterIds = pending.stream().map(Invitation::getInvitedBy).collect(Collectors.toSet());

        Map<UUID, String> roleNames = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
        Map<UUID, String> inviterNames = membershipRepository
                .findByOrganizationIdAndUserIdIn(orgId, inviterIds).stream()
                .collect(Collectors.toMap(OrganizationMembership::getUserId,
                        OrganizationMembership::getDisplayName, (a, b) -> a));

        List<InvitationView> views = pending.stream()
                .map(inv -> new InvitationView(
                        inv.getId(),
                        inv.getEmail(),
                        inv.getRoleId(),
                        roleNames.get(inv.getRoleId()),
                        inviterNames.getOrDefault(inv.getInvitedBy(), "Unknown"),
                        inv.getExpiresAt(),
                        inv.getCreatedAt()))
                .toList();
        return PageResponse.of(views);
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawToken) {
        Invitation invitation = findByToken(rawToken);
        if (!invitation.isPending() || invitation.isExpired()) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "That invitation is no longer valid");
        }
        Organization org = organizationService.requireOrg(invitation.getOrganizationId());
        Role role = roleService.requireRole(invitation.getRoleId());
        return new InvitationPreview(org.getName(), role.getName());
    }

    @Transactional
    public UUID accept(AcceptInvitationRequest request) {
        Invitation invitation = findByToken(request.token());
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw ApiException.of(ErrorCode.CONFLICT, "That invitation has already been accepted");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.of(ErrorCode.INVALID_STATE, "That invitation is no longer valid");
        }
        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw ApiException.of(ErrorCode.INVALID_STATE, "That invitation has expired");
        }

        User user = userService.findByEmail(invitation.getEmail())
                .orElseGet(() -> {
                    String name = request.fullName() != null && !request.fullName().isBlank()
                            ? request.fullName().trim()
                            : invitation.getEmail();
                    User created = userService.createPasswordlessUser(invitation.getEmail(), name);
                    return created;
                });

        memberService.addMember(invitation.getOrganizationId(), user.getId(),
                invitation.getRoleId(), invitation.getInvitedBy(), user);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitation.setAcceptedBy(user.getId());
        invitationRepository.save(invitation);

        events.publishEvent(AuditRequested.labelled(invitation.getOrganizationId(), user.getId(),
                "org.invite.accepted", "invitation", invitation.getId(), invitation.getEmail()));

        return invitation.getOrganizationId();
    }

    @Transactional
    public void revoke(UUID orgId, UUID invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        if (!invitation.getOrganizationId().equals(orgId)) {
            throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS, "That invitation is not in this organization");
        }
        if (invitation.getStatus() == InvitationStatus.PENDING) {
            invitation.setStatus(InvitationStatus.REVOKED);
            invitation.setRevokedAt(Instant.now());
            invitationRepository.save(invitation);
        }
    }

    @Transactional
    public IssuedInvite resend(UUID orgId, UUID invitationId, String inviterName) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        if (!invitation.getOrganizationId().equals(orgId)) {
            throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS, "That invitation is not in this organization");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.of(ErrorCode.INVALID_STATE, "Only pending invitations can be resent");
        }

        String rawToken = Ids.token();
        invitation.setTokenHash(sha256(rawToken));
        invitation.setExpiresAt(Instant.now().plus(INVITE_TTL));
        invitation.setReminderCount(invitation.getReminderCount() + 1);
        invitation.setLastSentAt(Instant.now());
        invitation = invitationRepository.save(invitation);

        Organization org = organizationService.requireOrg(orgId);
        Role role = roleService.requireRole(invitation.getRoleId());
        sendInviteEmail(org, inviterName, role, invitation.getEmail(), rawToken,
                invitation.getMessage(), invitation.getExpiresAt());
        return new IssuedInvite(invitation, rawToken);
    }

    private Invitation findByToken(String rawToken) {
        return invitationRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> ApiException.notFound("Invitation"));
    }

    private void sendInviteEmail(Organization org,
                                 String inviterName,
                                 Role role,
                                 String email,
                                 String rawToken,
                                 String message,
                                 Instant expiresAt) {
        // Console router path: /invite/:token, a path segment rather than a query parameter, and
        // singular. It was /invites/accept?token=, which matches no route — so the invitee fell
        // through to the catch-all, got redirected to a protected page and landed on the sign-in
        // form with no account to sign in to. The token is URL-safe base64, so it needs no encoding.
        String link = properties.urls().console() + "/invite/" + rawToken;
        mail.send(MailRequest.forOrganization(
                org.getId(),
                email,
                "org.invite",
                Map.of(
                        "inviterName", inviterName,
                        "organizationName", org.getName(),
                        "roleName", role.getName(),
                        "link", link,
                        "expiresOn", DateTimeFormatter.ISO_INSTANT.format(expiresAt),
                        "message", message != null ? message : ""),
                "invite:" + org.getId() + ":" + email));
    }

    static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
