package com.prabhix.platform.org.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.dto.OrgDtos.CreateOrganizationRequest;
import com.prabhix.platform.org.dto.OrgDtos.OrganizationView;
import com.prabhix.platform.org.dto.OrgDtos.UpdateOrganizationRequest;
import com.prabhix.platform.org.service.OrganizationDeletionService;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationDeletionService organizationDeletionService;

    @PostMapping
    @PreAuthorize(Authorize.AUTHENTICATED)
    public OrganizationView create(@CurrentUser PrabhixPrincipal principal,
                                     @Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.create(principal.userId(), request);
    }

    @GetMapping
    @PreAuthorize(Authorize.AUTHENTICATED)
    public List<OrganizationView> list(@CurrentUser PrabhixPrincipal principal) {
        return organizationService.listForUser(principal.userId());
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.ORG_READ)
    public OrganizationView get(@RequestParam UUID id) {
        return organizationService.get(id);
    }

    @PatchMapping
    @PreAuthorize(Authorize.ORG_UPDATE)
    public OrganizationView update(@RequestParam UUID id,
                                   @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.update(id, request);
    }

    @DeleteMapping
    @PreAuthorize(Authorize.ORG_DELETE)
    public ResponseEntity<Void> delete(@CurrentUser PrabhixPrincipal principal,
                                       @RequestParam UUID id,
                                       @RequestParam(defaultValue = "false") boolean confirm) {
        organizationDeletionService.delete(id, principal.userId(), confirm);
        return ResponseEntity.noContent().build();
    }
}
