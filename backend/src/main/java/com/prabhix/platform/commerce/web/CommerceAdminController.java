package com.prabhix.platform.commerce.web;

import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.service.CommerceDashboardService;
import com.prabhix.platform.commerce.service.CommerceSettingsService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oneops/commerce")
@RequiredArgsConstructor
public class CommerceAdminController {

    private final CommerceSettingsService settingsService;
    private final CommerceDashboardService dashboardService;

    @GetMapping("/settings")
    @PreAuthorize(Authorize.COMMERCE_SETTINGS_MANAGE)
    public CommerceDtos.SettingsView settings(@CurrentUser PrabhixPrincipal principal) {
        return settingsService.get(principal.requireOrganizationId());
    }

    @PutMapping("/settings")
    @PreAuthorize(Authorize.COMMERCE_SETTINGS_MANAGE)
    public CommerceDtos.SettingsView updateSettings(@CurrentUser PrabhixPrincipal principal,
                                                    @Valid @RequestBody CommerceDtos.UpdateSettingsRequest request) {
        return settingsService.update(principal, request);
    }

    @GetMapping("/dashboard")
    @PreAuthorize(Authorize.COMMERCE_ORDER_READ)
    public CommerceDtos.DashboardView dashboard(@CurrentUser PrabhixPrincipal principal) {
        return dashboardService.getDashboard(principal.requireOrganizationId());
    }
}
