package com.prabhix.platform.org.web;

import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.org.dto.OrgDtos;
import com.prabhix.platform.org.service.ApiKeyService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    @PreAuthorize(Authorize.ORG_API_KEY_MANAGE)
    public PageResponse<OrgDtos.ApiKeyView> list(@CurrentUser PrabhixPrincipal principal) {
        return apiKeyService.list(principal.requireOrganizationId());
    }

    @PostMapping
    @PreAuthorize(Authorize.ORG_API_KEY_MANAGE)
    public OrgDtos.CreatedApiKeyView create(@CurrentUser PrabhixPrincipal principal,
                                              @Valid @RequestBody OrgDtos.CreateApiKeyRequest request) {
        return apiKeyService.create(
                principal.requireOrganizationId(), principal.userId(), request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Authorize.ORG_API_KEY_MANAGE)
    public void revoke(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        apiKeyService.revoke(principal.requireOrganizationId(), id);
    }
}
