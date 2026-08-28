package com.prabhix.platform.org.web;

import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.dto.OrgDtos.CreateRoleRequest;
import com.prabhix.platform.org.dto.OrgDtos.RoleView;
import com.prabhix.platform.org.dto.OrgDtos.UpdateRoleRequest;
import com.prabhix.platform.org.service.RoleService;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize(Authorize.ORG_ROLE_READ)
    public PageResponse<RoleView> list(@CurrentUser PrabhixPrincipal principal) {
        return roleService.listRoles(principal.organizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.ORG_ROLE_MANAGE)
    public RoleView create(@CurrentUser PrabhixPrincipal principal,
                           @Valid @RequestBody CreateRoleRequest request) {
        return roleService.createCustomRole(principal.requireOrganizationId(), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Authorize.ORG_ROLE_MANAGE)
    public RoleView update(@CurrentUser PrabhixPrincipal principal,
                           @PathVariable UUID id,
                           @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.updateCustomRole(principal.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.ORG_ROLE_MANAGE)
    public void delete(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        roleService.deleteCustomRole(principal.requireOrganizationId(), id);
    }
}
