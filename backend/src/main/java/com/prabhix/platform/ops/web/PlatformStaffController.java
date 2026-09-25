package com.prabhix.platform.ops.web;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.ops.domain.PlatformStaffRole;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.service.PlatformStaffService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.jwt.TokenDenyList;
import com.prabhix.platform.security.rbac.Authorize;
import com.prabhix.platform.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who works here, and what they may do.
 *
 * <p>{@code SecurityConfig} gates the whole {@code /api/v1/oneops/admin/**} tree on PLATFORM_ADMIN, which is
 * now the coarse "is staff at all" check. Every handler here narrows further through
 * {@link PlatformStaffService}, because the point of the roles is that holding the flag is no longer
 * enough for the dangerous operations.
 */
@RestController
@RequestMapping("/api/v1/oneops/admin/platform/staff")
@RequiredArgsConstructor
public class PlatformStaffController {

    private final PlatformStaffService staff;
    private final TokenDenyList denyList;
    private final UserRepository users;
    private final StructuredEventLogger eventLogger;

    /** What the signed-in staff member holds, so the console can hide what they cannot use. */
    @GetMapping("/me")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public MyRoles myRoles(@CurrentUser PrabhixPrincipal principal) {
        return new MyRoles(staff.rolesFor(principal.userId()));
    }

    @GetMapping
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public List<GrantView> list(@CurrentUser PrabhixPrincipal principal) {
        staff.requireAny(principal.userId(), StaffRole.ROLE_ADMIN);
        return staff.listAll().stream().map(GrantView::of).toList();
    }

    @PostMapping("/grants")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public GrantView grant(@CurrentUser PrabhixPrincipal principal,
                           @Valid @RequestBody GrantRequest request) {
        PlatformStaffRole granted =
                staff.grant(principal.userId(), request.userId(), request.role(), request.note());
        return GrantView.of(granted);
    }

    @PostMapping("/revocations")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public void revoke(@CurrentUser PrabhixPrincipal principal,
                       @Valid @RequestBody GrantRequest request) {
        staff.revoke(principal.userId(), request.userId(), request.role(), request.note());
    }

    /**
     * Break glass: invalidate every token and session for one account, immediately.
     *
     * <p>SECURITY and OWNER only. This works while an attacker holds a valid session, which makes it
     * the most powerful thing in the admin surface and the thing most likely to be reached for in a
     * panic by whoever happens to be logged in. A support hire who can do this is a support hire who
     * can lock a customer out of their own business by mistake.
     */
    @PostMapping("/break-glass/users/revoke-tokens")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public Map<String, Object> revokeUserTokens(@CurrentUser PrabhixPrincipal principal,
                                                @RequestParam UUID userId,
                                                @Valid @RequestBody BreakGlassRequest request) {
        staff.requireAny(principal.userId(), StaffRole.BREAK_GLASS);

        if (!users.existsById(userId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "No such user");
        }

        denyList.revokeUser(userId);

        // logNow rather than the buffered path: if this is being used during an incident, the row has
        // to survive the process being killed a second later.
        eventLogger.logNow(LogEventCode.SECURITY_BREAK_GLASS_REVOKED,
                Map.of("subject", userId.toString(),
                        "actor", principal.userId().toString(),
                        "reason", request.reason(),
                        "scope", "all_tokens_and_sessions"));

        return Map.of("revoked", true, "userId", userId, "at", Instant.now());
    }

    public record MyRoles(Set<StaffRole> roles) {
    }

    public record GrantRequest(@NotNull UUID userId, @NotNull StaffRole role, String note) {
    }

    /** A reason is required, not optional: a revocation nobody can explain later is a liability. */
    public record BreakGlassRequest(@NotNull String reason) {
    }

    public record GrantView(UUID id,
                            UUID userId,
                            StaffRole role,
                            Instant grantedAt,
                            UUID grantedBy,
                            String note) {

        static GrantView of(PlatformStaffRole grant) {
            return new GrantView(grant.getId(), grant.getUserId(), grant.getRole(),
                    grant.getGrantedAt(), grant.getGrantedBy(), grant.getNote());
        }
    }
}
