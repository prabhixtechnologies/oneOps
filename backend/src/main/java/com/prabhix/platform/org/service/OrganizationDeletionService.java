package com.prabhix.platform.org.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.billing.service.EntitlementService;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.security.rbac.SystemRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Soft-deletes an organization with a mandatory cooling-off period before any hard purge.
 *
 * <p>Deletion is allowed only when:
 * <ul>
 *   <li>The caller holds the {@link SystemRole#OWNER} role in the target organization.</li>
 *   <li>The organization is not already deleted.</li>
 *   <li>{@code confirm=true} is supplied. This is always required: the call destroys every
 *       mailbox, thread, order, and file the organization owns.</li>
 * </ul>
 *
 * <p>On success the service cancels any live subscription immediately (so billing stops), marks
 * every active membership suspended, deny-lists tokens for affected users, and sets
 * {@code purge_scheduled_at} thirty days out. Data remains recoverable until that date; a
 * separate purge job (not invoked here) performs the irreversible removal.
 */
@Service
@RequiredArgsConstructor
public class OrganizationDeletionService {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final EntitlementService entitlementService;
    private final TokenDenyList tokenDenyList;
    private final PermissionResolver permissionResolver;
    private final ApplicationEventPublisher events;

    @Transactional
    public void delete(UUID orgId, UUID actorUserId, boolean confirm) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organization"));
        if (org.getDeletedAt() != null) {
            throw ApiException.of(ErrorCode.INVALID_STATE, "Organization is already scheduled for deletion");
        }

        OrganizationMembership actorMembership = membershipRepository
                .findByOrganizationIdAndUserId(orgId, actorUserId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER, "You are not a member of that organization"));
        if (actorMembership.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.forbidden("Your membership is not active");
        }

        Role ownerRole = roleRepository.findByOrganizationIdIsNullAndRoleKey(SystemRole.OWNER.name())
                .orElseThrow(() -> ApiException.of(ErrorCode.INTERNAL_ERROR, "System roles are not seeded"));
        if (!ownerRole.getId().equals(actorMembership.getRoleId())) {
            throw ApiException.forbidden("Only an organization owner may delete the organization");
        }

        // Confirmation is unconditional. Requiring it only when co-owners exist had it
        // backwards: the sole-owner case is the dangerous one, because there is nobody else
        // left who could notice or undo an accidental call.
        if (!confirm) {
            long owners = membershipRepository.countActiveByRole(orgId, ownerRole.getId());
            throw ApiException.of(ErrorCode.CONFLICT, owners > 1
                    ? "Other owners still exist and will lose access. Pass confirm=true to proceed."
                    : "You are the only owner, so nobody else can undo this. Pass confirm=true to proceed.");
        }

        Instant now = Instant.now();
        org.setDeletedAt(now);
        org.setPurgeScheduledAt(now.plus(RETENTION));
        org.setStatus(Organization.OrganizationStatus.DELETED);
        organizationRepository.save(org);

        cancelSubscription(orgId);
        suspendMembers(orgId);
        revokeMemberSessions(orgId);

        permissionResolver.evictOrganization(orgId);
        events.publishEvent(AuditRequested.labelled(orgId, actorUserId,
                "org.deleted", "organization", orgId, org.getName()));
    }

    private void cancelSubscription(UUID orgId) {
        subscriptionRepository.findByOrganizationIdAndStatusIn(orgId,
                        List.of(BillingEnums.SubscriptionStatus.TRIALING,
                                BillingEnums.SubscriptionStatus.ACTIVE,
                                BillingEnums.SubscriptionStatus.PAST_DUE,
                                BillingEnums.SubscriptionStatus.PAUSED))
                .ifPresent(subscription -> {
                    subscription.setStatus(BillingEnums.SubscriptionStatus.CANCELLED);
                    subscription.setCancelledAt(Instant.now());
                    subscription.setCancelAtPeriodEnd(false);
                    subscription.setCancellationReason("Organization deleted");
                    subscriptionRepository.save(subscription);
                    entitlementService.evictCache(orgId);
                });
    }

    private void suspendMembers(UUID orgId) {
        List<OrganizationMembership> members = membershipRepository.listMembersKeyset(
                orgId, MembershipStatus.ACTIVE.name(), null, null, null, null, null, 10_000);
        for (OrganizationMembership membership : members) {
            membership.setStatus(MembershipStatus.SUSPENDED);
            membershipRepository.save(membership);
            permissionResolver.evict(membership.getUserId(), orgId);
        }
    }

    private void revokeMemberSessions(UUID orgId) {
        List<OrganizationMembership> members = membershipRepository.listMembersKeyset(
                orgId, null, null, null, null, null, null, 10_000);
        for (OrganizationMembership membership : members) {
            tokenDenyList.revokeUser(membership.getUserId());
        }
    }
}
