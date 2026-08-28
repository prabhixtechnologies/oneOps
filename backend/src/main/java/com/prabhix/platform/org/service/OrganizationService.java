package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.OrganizationCreated;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.Organization.OrganizationStatus;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.dto.OrgDtos.CreateOrganizationRequest;
import com.prabhix.platform.org.dto.OrgDtos.OrganizationView;
import com.prabhix.platform.org.dto.OrgDtos.UpdateOrganizationRequest;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.rbac.SystemRole;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final PermissionResolver permissionResolver;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional
    public OrganizationView create(UUID creatorId, CreateOrganizationRequest request) {
        User creator = userService.requireActive(creatorId);
        Role ownerRole = roleRepository.findByOrganizationIdIsNullAndRoleKey(SystemRole.OWNER.name())
                .orElseThrow(() -> ApiException.of(
                        com.prabhix.platform.common.error.ErrorCode.INTERNAL_ERROR,
                        "System roles are not seeded"));

        String slug = uniquifySlug(Ids.slug(request.name()));

        Organization org = new Organization();
        org.setName(request.name().trim());
        org.setSlug(slug);
        org.setStatus(OrganizationStatus.TRIAL);
        org.setTrialEndsAt(Instant.now().plus(java.time.Duration.ofDays(properties.billing().trialDays())));
        org.setMemberCount(1);
        org.setSeatLimit(5);
        org = organizationRepository.save(org);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganizationId(org.getId());
        membership.setUserId(creatorId);
        membership.setRoleId(ownerRole.getId());
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setDisplayName(creator.effectiveDisplayName());
        membership.setEmail(creator.getEmail());
        membershipRepository.save(membership);

        userService.setDefaultOrganization(creatorId, org.getId());
        permissionResolver.evictUser(creatorId);
        events.publishEvent(new OrganizationCreated(org.getId(), creatorId));

        return toView(org);
    }

    @Transactional(readOnly = true)
    public OrganizationView get(UUID orgId) {
        return toView(requireOrg(orgId));
    }

    @Transactional
    public OrganizationView update(UUID orgId, UpdateOrganizationRequest request) {
        Organization org = requireOrg(orgId);
        if (request.name() != null && !request.name().isBlank()) {
            org.setName(request.name().trim());
        }
        if (request.legalName() != null) {
            org.setLegalName(request.legalName().isBlank() ? null : request.legalName().trim());
        }
        if (request.gstin() != null) {
            org.setGstin(request.gstin().isBlank() ? null : request.gstin().trim());
        }
        if (request.pan() != null) {
            org.setPan(request.pan().isBlank() ? null : request.pan().trim());
        }
        if (request.billingEmail() != null) {
            org.setBillingEmail(request.billingEmail().isBlank() ? null : request.billingEmail().trim());
        }
        if (request.phone() != null) {
            org.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.website() != null) {
            org.setWebsite(request.website().isBlank() ? null : request.website().trim());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            org.setTimezone(request.timezone().trim());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            org.setLocale(request.locale().trim());
        }
        return toView(organizationRepository.save(org));
    }

    @Transactional(readOnly = true)
    public List<OrganizationView> listForUser(UUID userId) {
        List<OrganizationMembership> memberships =
                membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        Map<UUID, Organization> orgsById = organizationRepository.findAllById(
                memberships.stream().map(OrganizationMembership::getOrganizationId).toList())
                .stream()
                .filter(org -> org.getDeletedAt() == null)
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
        return memberships.stream()
                .map(m -> orgsById.get(m.getOrganizationId()))
                .filter(Objects::nonNull)
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Organization requireOrg(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organization"));
        if (org.getDeletedAt() != null) {
            throw ApiException.notFound("Organization");
        }
        return org;
    }

    @Transactional(readOnly = true)
    public void requireActiveMembership(UUID orgId, UUID userId) {
        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserId(orgId, userId)
                .orElseThrow(() -> ApiException.of(
                        com.prabhix.platform.common.error.ErrorCode.NOT_A_MEMBER,
                        "You are not a member of that organization"));
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.of(
                    com.prabhix.platform.common.error.ErrorCode.MEMBERSHIP_SUSPENDED,
                    "Your membership in that organization is not active");
        }
    }

    private String uniquifySlug(String base) {
        String candidate = base.isBlank() ? "org" : base;
        if (candidate.length() < 2) {
            candidate = candidate + "-org";
        }
        String slug = candidate;
        int suffix = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = candidate + "-" + suffix++;
        }
        return slug;
    }

    private OrganizationView toView(Organization org) {
        return new OrganizationView(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getStatus().name(),
                org.getMemberCount(),
                org.getSeatLimit(),
                org.getTrialEndsAt(),
                org.getTimezone(),
                org.getLocale(),
                org.getCurrency(),
                org.getCreatedAt());
    }
}
