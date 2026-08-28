package com.prabhix.platform.org.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.domain.Role;
import com.prabhix.platform.org.dto.OrgDtos.CreateRoleRequest;
import com.prabhix.platform.org.dto.OrgDtos.RoleView;
import com.prabhix.platform.org.dto.OrgDtos.UpdateRoleRequest;
import com.prabhix.platform.org.repository.RoleRepository;
import com.prabhix.platform.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionResolver permissionResolver;

    @Transactional(readOnly = true)
    public PageResponse<RoleView> listRoles(UUID organizationId) {
        List<Role> system = roleRepository.findByOrganizationIdIsNullOrderByRankAsc();
        List<Role> custom = organizationId != null
                ? roleRepository.findByOrganizationIdOrderByRankAsc(organizationId)
                : List.of();
        List<RoleView> views = Stream.concat(system.stream(), custom.stream())
                .map(this::toView)
                .toList();
        return PageResponse.of(views);
    }

    @Transactional
    public RoleView createCustomRole(UUID organizationId, CreateRoleRequest request) {
        String roleKey = request.roleKey().trim().toUpperCase();
        if (roleRepository.findByOrganizationIdIsNullAndRoleKey(roleKey).isPresent()) {
            throw ApiException.of(ErrorCode.CONFLICT, "That role key is reserved by a system role");
        }
        if (roleRepository.existsByOrganizationIdAndRoleKey(organizationId, roleKey)) {
            throw ApiException.of(ErrorCode.ALREADY_EXISTS, "A role with that key already exists");
        }

        Set<String> permissions = validatePermissions(request.permissions());

        Role role = new Role();
        role.setOrganizationId(organizationId);
        role.setRoleKey(roleKey);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setSystem(false);
        role.setPermissionCodes(permissions);
        role = roleRepository.save(role);

        permissionResolver.evictOrganization(organizationId);
        return toView(role);
    }

    @Transactional
    public RoleView updateCustomRole(UUID organizationId, UUID roleId, UpdateRoleRequest request) {
        Role role = requireCustomRole(organizationId, roleId);

        if (request.name() != null && !request.name().isBlank()) {
            role.setName(request.name().trim());
        }
        if (request.description() != null) {
            role.setDescription(request.description().isBlank() ? null : request.description().trim());
        }
        if (request.permissions() != null) {
            role.setPermissionCodes(validatePermissions(request.permissions()));
        }
        role = roleRepository.save(role);

        permissionResolver.evictOrganization(organizationId);
        return toView(role);
    }

    @Transactional
    public void deleteCustomRole(UUID organizationId, UUID roleId) {
        Role role = requireCustomRole(organizationId, roleId);
        roleRepository.delete(role);
        permissionResolver.evictOrganization(organizationId);
    }

    @Transactional(readOnly = true)
    public Role requireRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("Role"));
    }

    private Role requireCustomRole(UUID organizationId, UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("Role"));
        if (role.isSystemRole()) {
            throw ApiException.forbidden("System roles cannot be modified");
        }
        if (!organizationId.equals(role.getOrganizationId())) {
            throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS, "That role does not belong to this organization");
        }
        return role;
    }

    private Set<String> validatePermissions(Set<String> requested) {
        Set<String> validated = new HashSet<>();
        for (String code : requested) {
            Permission permission = Permission.parse(code).orElse(null);
            if (permission == null) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Unknown permission: " + code);
            }
            if (permission == Permission.PLATFORM_ADMIN
                    || permission == Permission.SITE_LEAD_READ
                    || permission == Permission.SITE_LEAD_MANAGE
                    || permission == Permission.SITE_SUBSCRIBER_READ
                    || permission == Permission.SITE_APPLICATION_READ
                    || permission == Permission.SITE_APPLICATION_MANAGE) {
                throw ApiException.forbidden(permission.name() + " cannot be assigned to a customer role");
            }
            validated.add(permission.name());
        }
        return validated;
    }

    private RoleView toView(Role role) {
        return new RoleView(
                role.getId(),
                role.getRoleKey(),
                role.getName(),
                role.getDescription(),
                role.isSystem(),
                role.getRank(),
                Set.copyOf(role.getPermissionCodes()));
    }
}
