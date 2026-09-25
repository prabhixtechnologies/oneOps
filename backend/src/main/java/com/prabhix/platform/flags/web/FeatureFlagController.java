package com.prabhix.platform.flags.web;

import com.prabhix.platform.flags.dto.FlagDtos;
import com.prabhix.platform.flags.service.FeatureFlagService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oneops/flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping
    @PreAuthorize(Authorize.AUTHENTICATED)
    public FlagDtos.EffectiveFlagsDetailed list(@CurrentUser PrabhixPrincipal principal) {
        return featureFlagService.effectiveFlagsDetailed(principal.requireOrganizationId());
    }

    @PutMapping
    @PreAuthorize(Authorize.ORG_UPDATE)
    public FlagDtos.FlagDetail setOverride(@CurrentUser PrabhixPrincipal principal,
                                           @RequestParam String key,
                                           @Valid @RequestBody FlagDtos.SetOverrideRequest request) {
        return featureFlagService.setOverride(
                principal.requireOrganizationId(), principal.userId(), key, request);
    }

    @DeleteMapping
    @PreAuthorize(Authorize.ORG_UPDATE)
    public ResponseEntity<Void> clearOverride(@CurrentUser PrabhixPrincipal principal,
                                              @RequestParam String key) {
        featureFlagService.clearOverride(principal.requireOrganizationId(), principal.userId(), key);
        return ResponseEntity.noContent().build();
    }
}
