package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.dto.OrgDtos.ChangeMemberRoleRequest;
import com.prabhix.platform.org.dto.OrgDtos.MemberListQuery;
import com.prabhix.platform.org.dto.OrgDtos.MemberView;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationMemberService {

    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final PermissionResolver permissionResolver;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;
    private final EntitlementGate entitlements;

    @Transactional(readOnly = true)
    public CursorPage<MemberView> listMembers(UUID orgId, MemberListQuery query) {
        int limit = properties.limits().clampPageSize(query.limit());
        Cursor cursor = Cursor.decode(query.cursor());

        MembershipStatus statusFilter = parseStatus(query.status());
        String statusParam = statusFilter != null ? statusFilter.name() : null;

        List<OrganizationMembership> fetched = membershipRepository.listMembersKeyset(
                orgId,
                statusParam,
                query.roleId(),
                blankToNull(query.department()),
                blankToNull(query.search()),
                cursor != null ? cursor.timestamp() : null,
                cursor != null ? cursor.id() : null,
                limit + 1);

        Map<UUID, Role> rolesById = roleRepository.findAllById(
                fetched.stream().map(OrganizationMembership::getRoleId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));

        return CursorPage.of(fetched, limit, m -> toView(m, rolesById.get(m.getRoleId())),
                m -> Cursor.of(m.getCreatedAt(), m.getId()).encode());
    }

    @Transactional
    public MemberView changeRole(UUID orgId, UUID memberId, ChangeMemberRoleRequest request, UUID actorId) {
        OrganizationMembership membership = requireMembership(orgId, memberId);
        Role newRole = roleRepository.findById(request.roleId())
                .orElseThrow(() -> ApiException.notFound("Role"));
        if (newRole.isSystemRole() || newRole.getOrganizationId() == null
                || !newRole.getOrganizationId().equals(orgId)) {
            if (!newRole.isSystemRole()) {
                throw ApiException.forbidden("That role does not belong to this organization");
            }
        }

        UUID oldRoleId = membership.getRoleId();
        membership.setRoleId(newRole.getId());
        membershipRepository.save(membership);

        permissionResolver.evict(membership.getUserId(), orgId);

        events.publishEvent(AuditRequested.changed(orgId, actorId, "org.member.role_changed",
                "organization_membership", membership.getId(),
                Map.of("roleId", Map.of("from", oldRoleId, "to", newRole.getId()))));

        return toView(membership, newRole);
    }

    @Transactional
    public void suspend(UUID orgId, UUID memberId, UUID actorId) {
        OrganizationMembership membership = requireMembership(orgId, memberId);
        membership.setStatus(MembershipStatus.SUSPENDED);
        membershipRepository.save(membership);
        adjustMemberCount(orgId, -1);
        permissionResolver.evict(membership.getUserId(), orgId);
    }

    @Transactional
    public void remove(UUID orgId, UUID memberId, UUID actorId, String actorEmail) {
        OrganizationMembership membership = requireMembership(orgId, memberId);
        if (membership.getStatus() == MembershipStatus.LEFT) {
            return;
        }
        membership.setStatus(MembershipStatus.LEFT);
        membershipRepository.save(membership);
        adjustMemberCount(orgId, -1);
        permissionResolver.evict(membership.getUserId(), orgId);

        events.publishEvent(new AuditRequested(orgId, actorId, actorEmail, "org.member.removed",
                "organization_membership", membership.getId(), membership.getDisplayName(),
                null, Map.of(), true));
    }

    @Transactional
    public OrganizationMembership addMember(UUID orgId,
                                            UUID userId,
                                            UUID roleId,
                                            UUID invitedBy,
                                            User user) {
        enforceSeatLimit(orgId);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganizationId(orgId);
        membership.setUserId(userId);
        membership.setRoleId(roleId);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setDisplayName(user.effectiveDisplayName());
        membership.setEmail(user.getEmail());
        membership.setInvitedBy(invitedBy);
        membership = membershipRepository.save(membership);

        adjustMemberCount(orgId, 1);
        permissionResolver.evict(userId, orgId);
        return membership;
    }

    @Transactional
    public void syncDisplayName(UUID userId, String displayName, String email) {
        membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)
                .forEach(m -> {
                    m.setDisplayName(displayName);
                    m.setEmail(email);
                    membershipRepository.save(m);
                });
    }

    private void enforceSeatLimit(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organization"));
        int maxMembers = Math.min(org.getSeatLimit(), properties.limits().maxMembersPerOrganization());
        if (org.getMemberCount() >= maxMembers) {
            throw ApiException.of(ErrorCode.SEAT_LIMIT_REACHED,
                    "This organization has reached its member limit");
        }
        // The cached seat count above can drift from the subscription; the plan is authoritative.
        entitlements.requireMemberSeat(orgId, org.getMemberCount());
    }

    private void adjustMemberCount(UUID orgId, int delta) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organization"));
        org.setMemberCount(Math.max(0, org.getMemberCount() + delta));
        organizationRepository.save(org);
    }

    private OrganizationMembership requireMembership(UUID orgId, UUID memberId) {
        OrganizationMembership membership = membershipRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("Member"));
        if (!membership.getOrganizationId().equals(orgId)) {
            throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS, "That member is not in this organization");
        }
        return membership;
    }

    private MembershipStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return MembershipStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Invalid membership status filter");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MemberView toView(OrganizationMembership membership, Role role) {
        return new MemberView(
                membership.getId(),
                membership.getUserId(),
                membership.getDisplayName(),
                membership.getEmail(),
                membership.getRoleId(),
                role != null ? role.getName() : null,
                membership.getStatus().name(),
                membership.getDepartment(),
                membership.getEmployeeId(),
                membership.getJoinedAt(),
                membership.getLastActiveAt());
    }
}
