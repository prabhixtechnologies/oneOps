package com.prabhix.platform.dashboard.web;

import com.prabhix.platform.dashboard.dto.DashboardDtos;
import com.prabhix.platform.dashboard.service.DashboardService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oneops/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize(Authorize.ORG_READ)
    public DashboardDtos.DashboardResponse get(@CurrentUser PrabhixPrincipal principal) {
        return dashboardService.getDashboard(principal.requireOrganizationId());
    }
}
