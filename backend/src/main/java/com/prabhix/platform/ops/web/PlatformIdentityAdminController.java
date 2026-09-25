package com.prabhix.platform.ops.web;

import com.prabhix.identity.client.IdentityAdmin.EventQuery;
import com.prabhix.identity.client.IdentityAdmin.UserSearch;
import com.prabhix.platform.ops.client.IdentityAdminClient;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Identity administration, proxied so the browser never talks to Identity directly.
 *
 * <p>The SECURITY role gate is inside {@link IdentityAdminClient}: Identity itself only checks the
 * service token and records who asked.
 */
@RestController
@RequestMapping("/api/v1/oneops/admin/platform/identity")
@RequiredArgsConstructor
public class PlatformIdentityAdminController {

    private final IdentityAdminClient identity;

    @GetMapping("/users")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object searchUsers(@CurrentUser PrabhixPrincipal principal,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(required = false) Integer limit) {
        return identity.searchUsers(principal.userId(), new UserSearch(q, status, cursor, limit));
    }

    @GetMapping(value = "/users", params = "id")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object getUser(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        return identity.getUser(principal.userId(), id);
    }

    @PostMapping("/users/disable")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public void disable(@CurrentUser PrabhixPrincipal principal,
                        @RequestParam UUID id,
                        @RequestBody(required = false) Map<String, String> body) {
        identity.disableUser(principal.userId(), id, reason(body));
    }

    @PostMapping("/users/enable")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public void enable(@CurrentUser PrabhixPrincipal principal,
                       @RequestParam UUID id,
                       @RequestBody(required = false) Map<String, String> body) {
        identity.enableUser(principal.userId(), id, reason(body));
    }

    @PostMapping("/users/unlock")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public void unlock(@CurrentUser PrabhixPrincipal principal,
                       @RequestParam UUID id,
                       @RequestBody(required = false) Map<String, String> body) {
        identity.unlockUser(principal.userId(), id, reason(body));
    }

    @PostMapping("/users/force-reset")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public void forceReset(@CurrentUser PrabhixPrincipal principal,
                           @RequestParam UUID id,
                           @RequestBody(required = false) Map<String, String> body) {
        identity.forcePasswordReset(principal.userId(), id, reason(body));
    }

    @PostMapping("/users/revoke-sessions")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public void revokeSessions(@CurrentUser PrabhixPrincipal principal,
                               @RequestParam UUID id,
                               @RequestBody(required = false) Map<String, String> body) {
        identity.revokeSessions(principal.userId(), id, reason(body));
    }

    @GetMapping("/clients")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object clients(@CurrentUser PrabhixPrincipal principal) {
        return identity.clients(principal.userId());
    }

    @GetMapping("/keys")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object keys(@CurrentUser PrabhixPrincipal principal) {
        return identity.keys(principal.userId());
    }

    @GetMapping("/events")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Object events(@CurrentUser PrabhixPrincipal principal,
                         @RequestParam(required = false) UUID userId,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) Instant since,
                         @RequestParam(required = false) String cursor,
                         @RequestParam(required = false) Integer limit) {
        return identity.events(principal.userId(), new EventQuery(userId, type, since, cursor, limit));
    }

    private static String reason(Map<String, String> body) {
        if (body == null) {
            return null;
        }
        String reason = body.get("reason");
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
