package com.prabhix.platform.ops.web;

import com.prabhix.platform.ops.client.MobiStackAdminClient;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.service.PlatformStaffService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MobiStack platform admin, proxied so the browser never talks to MobiStack directly.
 *
 * <p>Shops, live users, flags and the commons queue are SUPPORT. Plans sit with BILLING — a support
 * hire answering a ticket has no reason to change what a shop pays.
 */
@RestController
@RequestMapping("/api/v1/oneops/admin/platform")
@RequiredArgsConstructor
public class PlatformMobiStackAdminController {

    private final MobiStackAdminClient mobistack;
    private final PlatformStaffService staff;

    @GetMapping("/mobistack/workspaces")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object workspaces(@CurrentUser PrabhixPrincipal principal) {
        requireSupport(principal);
        return mobistack.get(principal.userId(), "/workspaces");
    }

    @PostMapping("/mobistack/workspaces/suspend")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object suspend(@CurrentUser PrabhixPrincipal principal,
                          @RequestParam UUID id,
                          @RequestBody(required = false) Map<String, String> body) {
        requireSupport(principal);
        return mobistack.post(principal.userId(), "/workspaces/suspend?id=" + id, Map.of(), reason(body));
    }

    @PostMapping("/mobistack/workspaces/activate")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object activate(@CurrentUser PrabhixPrincipal principal,
                           @RequestParam UUID id,
                           @RequestBody(required = false) Map<String, String> body) {
        requireSupport(principal);
        return mobistack.post(principal.userId(), "/workspaces/activate?id=" + id, Map.of(), reason(body));
    }

    @PostMapping("/mobistack/workspaces/screens")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object screens(@CurrentUser PrabhixPrincipal principal,
                          @RequestParam UUID id,
                          @RequestBody Map<String, Object> body) {
        requireSupport(principal);
        return mobistack.post(principal.userId(), "/workspaces/screens?id=" + id, body, null);
    }

    @GetMapping("/mobistack/live")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object live(@CurrentUser PrabhixPrincipal principal) {
        requireSupport(principal);
        return mobistack.get(principal.userId(), "/live");
    }

    @PostMapping("/mobistack/live/kick")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object kick(@CurrentUser PrabhixPrincipal principal,
                       @RequestParam UUID userId,
                       @RequestBody(required = false) Map<String, String> body) {
        requireSupport(principal);
        return mobistack.post(principal.userId(), "/live/kick?userId=" + userId,
                body == null ? Map.of() : body, reason(body));
    }

    @GetMapping("/mobistack/feature-flags")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object flags(@CurrentUser PrabhixPrincipal principal) {
        requireSupport(principal);
        return mobistack.get(principal.userId(), "/feature-flags");
    }

    @PutMapping("/mobistack/feature-flags")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object upsertFlag(@CurrentUser PrabhixPrincipal principal, @RequestBody Map<String, Object> body) {
        requireSupport(principal);
        return mobistack.put(principal.userId(), "/feature-flags", body, null);
    }

    @GetMapping("/mobistack/app-releases")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object releases(@CurrentUser PrabhixPrincipal principal) {
        requireSupport(principal);
        return mobistack.get(principal.userId(), "/app-releases");
    }

    @PutMapping("/mobistack/app-releases")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object updateRelease(@CurrentUser PrabhixPrincipal principal,
                                @RequestParam String platform,
                                @RequestBody Map<String, Object> body) {
        requireSupport(principal);
        return mobistack.put(principal.userId(), "/app-releases?platform=" + platform, body, null);
    }

    @GetMapping("/mobistack/support")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object support(@CurrentUser PrabhixPrincipal principal,
                          @RequestParam(required = false) String status) {
        requireSupport(principal);
        return mobistack.get(principal.userId(), "/support", Map.of("status", status == null ? "" : status));
    }

    @PostMapping("/mobistack/support/messages")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object supportReply(@CurrentUser PrabhixPrincipal principal,
                               @RequestParam UUID id,
                               @RequestBody Map<String, String> body) {
        requireSupport(principal);
        String message = body.get("message") != null ? body.get("message") : body.get("body");
        return mobistack.post(principal.userId(), "/support/messages?id=" + id,
                Map.of("message", message == null ? "" : message), null);
    }

    @PostMapping("/mobistack/support/resolve")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object supportResolve(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        requireSupport(principal);
        return mobistack.post(principal.userId(), "/support/resolve?id=" + id, Map.of(), null);
    }

    @GetMapping("/mobistack/plans")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object plans(@CurrentUser PrabhixPrincipal principal) {
        requireBilling(principal);
        return mobistack.get(principal.userId(), "/plans");
    }

    @GetMapping("/mobistack/plan-features")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object planFeatures(@CurrentUser PrabhixPrincipal principal) {
        requireBilling(principal);
        return mobistack.get(principal.userId(), "/plan-features");
    }

    @PostMapping("/mobistack/plans")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object createPlan(@CurrentUser PrabhixPrincipal principal, @RequestBody Map<String, Object> body) {
        requireBilling(principal);
        return mobistack.post(principal.userId(), "/plans", body, null);
    }

    @PutMapping("/mobistack/plans")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object updatePlan(@CurrentUser PrabhixPrincipal principal,
                             @RequestParam UUID id,
                             @RequestBody Map<String, Object> body) {
        requireBilling(principal);
        return mobistack.put(principal.userId(), "/plans?id=" + id, body, null);
    }

    @PostMapping("/mobistack/workspaces/plan")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object assignPlan(@CurrentUser PrabhixPrincipal principal,
                             @RequestParam UUID id,
                             @RequestBody Map<String, Object> body) {
        requireBilling(principal);
        return mobistack.post(principal.userId(), "/workspaces/plan?id=" + id, body, null);
    }

    @GetMapping("/mobistack/billing/orders")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object orders(@CurrentUser PrabhixPrincipal principal) {
        requireBilling(principal);
        return mobistack.get(principal.userId(), "/billing/orders");
    }

    @GetMapping("/mobistack/billing/revenue")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object mobiRevenue(@CurrentUser PrabhixPrincipal principal) {
        requireBilling(principal);
        return mobistack.get(principal.userId(), "/billing/revenue");
    }

    @GetMapping("/commons/queue")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object commonsQueue(@CurrentUser PrabhixPrincipal principal,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        requireSupport(principal);
        return mobistack.get(principal.userId(), "/commons/queue",
                Map.of("page", page, "size", size));
    }

    @PostMapping("/commons/accept")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object commonsAccept(@CurrentUser PrabhixPrincipal principal,
                                @RequestParam UUID id,
                                @RequestBody(required = false) Map<String, String> body) {
        requireSupport(principal);
        return mobistack.post(principal.userId(), "/commons/accept?id=" + id,
                body == null ? Map.of() : body, reason(body));
    }

    @PostMapping("/commons/reject")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object commonsReject(@CurrentUser PrabhixPrincipal principal,
                                @RequestParam UUID id,
                                @RequestBody(required = false) Map<String, String> body) {
        requireSupport(principal);
        return mobistack.post(principal.userId(), "/commons/reject?id=" + id,
                body == null ? Map.of() : body, reason(body));
    }

    private void requireSupport(PrabhixPrincipal principal) {
        staff.requireAny(principal.userId(), Set.of(StaffRole.SUPPORT));
    }

    private void requireBilling(PrabhixPrincipal principal) {
        staff.requireAny(principal.userId(), Set.of(StaffRole.BILLING));
    }

    private static String reason(Map<String, String> body) {
        if (body == null) {
            return null;
        }
        String reason = body.get("reason");
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
