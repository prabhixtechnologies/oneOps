package com.prabhix.platform.ops.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.dto.OpsDtos;
import com.prabhix.platform.ops.service.PlatformBillingRevenueService;
import com.prabhix.platform.ops.service.PlatformOverviewService;
import com.prabhix.platform.ops.service.PlatformStaffService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-tenant operator endpoints.
 *
 * <p>{@code SecurityConfig} already gates the whole {@code /api/v1/admin/**} tree on
 * PLATFORM_ADMIN. The annotations repeat it per method so that moving a handler out of this
 * prefix cannot silently drop the check. Fine-grained staff roles are checked through
 * {@link com.prabhix.platform.ops.service.PlatformStaffService}.
 */
@RestController
@RequestMapping("/api/v1/admin/platform")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformOverviewService platformOverviewService;
    private final PlatformBillingRevenueService platformBillingRevenueService;
    private final PlatformStaffService staff;

    @GetMapping("/overview")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.PlatformOverview overview() {
        return platformOverviewService.overview();
    }

    @GetMapping("/tenants")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public CursorPage<OpsDtos.TenantSummary> listTenants(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        staff.requireAny(principal.userId(), java.util.Set.of(StaffRole.SUPPORT));
        return platformOverviewService.listTenants(status, cursor, limit);
    }

    @GetMapping("/billing/revenue")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.PlatformBillingRevenue billingRevenue(@CurrentUser PrabhixPrincipal principal) {
        staff.requireAny(principal.userId(), java.util.Set.of(StaffRole.BILLING));
        return platformBillingRevenueService.revenue(principal.userId());
    }
}
