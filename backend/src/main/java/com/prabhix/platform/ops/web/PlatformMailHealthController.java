package com.prabhix.platform.ops.web;

import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.dto.OpsDtos;
import com.prabhix.platform.ops.service.PlatformMailHealthService;
import com.prabhix.platform.ops.service.PlatformStaffService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/platform")
@RequiredArgsConstructor
public class PlatformMailHealthController {

    private final PlatformMailHealthService mailHealth;
    private final PlatformStaffService staff;

    @GetMapping("/mail/health")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.MailHealthResponse mailHealth(@CurrentUser PrabhixPrincipal principal) {
        staff.requireAny(principal.userId(), Set.of(StaffRole.SUPPORT));
        return mailHealth.health();
    }
}
