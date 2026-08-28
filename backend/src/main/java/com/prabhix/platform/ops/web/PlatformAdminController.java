package com.prabhix.platform.ops.web;

import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.ops.dto.OpsDtos;
import com.prabhix.platform.ops.service.PlatformOverviewService;
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
 * prefix cannot silently drop the check.
 */
@RestController
@RequestMapping("/api/v1/admin/platform")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformOverviewService platformOverviewService;

    @GetMapping("/overview")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.PlatformOverview overview() {
        return platformOverviewService.overview();
    }

    @GetMapping("/tenants")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public CursorPage<OpsDtos.TenantSummary> listTenants(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return platformOverviewService.listTenants(status, cursor, limit);
    }
}
