package com.prabhix.platform.security.jwt;

import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.service.ActiveOrganizationResolver;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.service.PlatformStaffService;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.security.tenant.ImpersonationAuditor;
import com.prabhix.platform.security.tenant.TenantContext;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import com.prabhix.platform.user.service.IdentityUserMirror;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who a request is acting as, when the token itself says nothing about it.
 *
 * <p>An identity token carries a subject and nothing else — no organization, no permissions, no staff
 * flag ({@link JwtServiceIdentityTest} pins that it cannot, even if identity were compromised into
 * minting the claims). Everything about authority is therefore decided here, per request, from this
 * database. Once sign-in moves behind the hosted login page this is the only tenancy gate the
 * platform has, so the decisions are pinned rather than left to a reading of the filter.
 *
 * <p>The interesting cases are all about the gap between "no organization was named" and "an
 * organization was named that the caller has no claim to". The first is ordinary — a console's very
 * first call cannot name one, because that call is how it finds out — and must resolve to something
 * usable. The second is a tenancy breach and must not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityTokenTenancyTest {

    @Mock private JwtService jwtService;
    @Mock private TokenDenyList denyList;
    @Mock private StructuredEventLogger eventLogger;
    @Mock private ImpersonationAuditor impersonationAuditor;
    @Mock private PermissionResolver permissionResolver;
    @Mock private ActiveOrganizationResolver activeOrganizations;
    @Mock private OrganizationMembershipRepository memberships;
    @Mock private UserRepository users;
    @Mock private IdentityUserMirror mirror;
    @Mock private PlatformStaffService platformStaff;

    private JwtAuthenticationFilter filter;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID homeOrg = UUID.randomUUID();
    private final UUID otherOrg = UUID.randomUUID();

    /** What the chain saw, captured there because the filter clears both contexts on the way out. */
    private final AtomicReference<PrabhixPrincipal> seen = new AtomicReference<>();
    private final AtomicReference<UUID> tenant = new AtomicReference<>();

    private FilterChain capturing() {
        return (request, response) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            seen.set(authentication == null ? null : (PrabhixPrincipal) authentication.getPrincipal());
            tenant.set(TenantContext.current().orElse(null));
        };
    }

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, denyList, new ObjectMapper(), eventLogger,
                impersonationAuditor, permissionResolver, activeOrganizations, memberships, users,
                mirror, platformStaff);

        when(denyList.isRevoked(any(), any(), any())).thenReturn(false);
        when(jwtService.parseDetailed(any())).thenReturn(new JwtService.ParsedToken(
                // Exactly what identity gives us: a subject, and no authority of any kind.
                new PrabhixPrincipal(userId, "someone@example.com", "Someone", null, Set.of(), sessionId, false),
                Instant.now(),
                JwtService.TokenSource.IDENTITY));
        when(permissionResolver.resolve(any(), any())).thenReturn(Set.of(Permission.MAIL_READ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ the gap this change closed

    @Test
    void aRequestNamingNoOrganizationGetsTheOneSignInWouldHavePicked() {
        // The console's first call. It cannot send the header, because /auth/me is where it learns
        // which organization it is in — and its schema rejects a reply without one.
        givenUser(homeOrg, false);
        when(activeOrganizations.resolve(userId, homeOrg)).thenReturn(homeOrg);
        when(memberships.existsActiveMembership(homeOrg, userId)).thenReturn(true);

        MockHttpServletResponse response = invoke(null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get().organizationId()).isEqualTo(homeOrg);
        assertThat(tenant.get()).isEqualTo(homeOrg);
    }

    @Test
    void anExplicitOrganizationIsNotSecondGuessedByTheFallback() {
        // Switching organization, and staff impersonation, both work by sending this header. If the
        // fallback ran anyway it would be a vote against the caller's own choice.
        givenUser(homeOrg, false);
        when(memberships.existsActiveMembership(otherOrg, userId)).thenReturn(true);

        MockHttpServletResponse response = invoke(otherOrg.toString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get().organizationId()).isEqualTo(otherOrg);
        verifyNoInteractions(activeOrganizations);
    }

    @Test
    void somebodyInSeveralOrganizationsWithNoDefaultIsAskedRatherThanGuessedFor() {
        // Null is the honest answer, not a failure: /users/me and the organization list have to work
        // before anyone has chosen. What must not happen is being dropped into an arbitrary tenant.
        givenUser(null, false);
        when(activeOrganizations.resolve(userId, null)).thenReturn(null);

        MockHttpServletResponse response = invoke(null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get().organizationId()).isNull();
        assertThat(tenant.get()).isNull();
        // No organization to check membership against, so nothing should have been asked.
        verify(memberships, never()).existsActiveMembership(any(), any());
    }

    // ------------------------------------------------------------------ the gate

    @Test
    void anOrdinaryUserCannotNameATenantTheyDoNotBelongTo() throws Exception {
        // The whole tenancy model in one assertion. The header is caller-supplied, so if it were
        // trusted, any signed-in customer could read any other customer's data by editing a request.
        givenUser(homeOrg, false);
        when(memberships.existsActiveMembership(otherOrg, userId)).thenReturn(false);

        MockHttpServletResponse response = invoke(otherOrg.toString());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(ErrorCode.CROSS_TENANT_ACCESS.name());
        // Refused before the chain, not filtered inside it: no handler ever ran.
        assertThat(seen.get()).isNull();
    }

    @Test
    void aSuspendedMembershipIsNotAMembership() {
        // Same refusal by a different route: the repository's active check is what decides, so a
        // membership that was revoked or suspended reads as absence rather than as history.
        givenUser(otherOrg, false);
        when(activeOrganizations.resolve(userId, otherOrg)).thenReturn(otherOrg);
        when(memberships.existsActiveMembership(otherOrg, userId)).thenReturn(false);

        assertThat(invoke(null).getStatus()).isEqualTo(403);
    }

    @Test
    void staffMayReachIntoATenantButOnlyTheStaffWhoseJobIsTenantContent() {
        givenUser(homeOrg, true);
        when(memberships.existsActiveMembership(otherOrg, userId)).thenReturn(false);

        MockHttpServletResponse response = invoke(otherOrg.toString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get().organizationId()).isEqualTo(otherOrg);
        verify(platformStaff).requireAny(userId, StaffRole.TENANT_ACCESS);
        // Reaching in is recorded. An unaudited look at a customer's data is the one thing staff
        // access must never be.
        verify(impersonationAuditor).recordAccess(userId, sessionId, null, otherOrg);
    }

    @Test
    void theStaffFlagAloneDoesNotOpenATenant() {
        // BILLING and OPERATOR carry the flag and are deliberately excluded from TENANT_ACCESS:
        // whoever is replaying a stuck mail queue has no reason to read the mail in it.
        givenUser(homeOrg, true);
        when(memberships.existsActiveMembership(otherOrg, userId)).thenReturn(false);
        doThrow(ApiException.of(ErrorCode.FORBIDDEN, "Your platform role does not include this action."))
                .when(platformStaff).requireAny(userId, StaffRole.TENANT_ACCESS);

        assertThat(invoke(otherOrg.toString()).getStatus()).isEqualTo(403);
        assertThat(seen.get()).isNull();
    }

    // ------------------------------------------------------------------ where authority comes from

    @Test
    void permissionsAreReadFromThisDatabaseForThisPairing() {
        // Not from the token, and not from the user row: from the user-and-organization pair, so a
        // role revoked a second ago is gone on the next request rather than when the token expires.
        givenUser(homeOrg, false);
        when(activeOrganizations.resolve(userId, homeOrg)).thenReturn(homeOrg);
        when(memberships.existsActiveMembership(homeOrg, userId)).thenReturn(true);
        when(permissionResolver.resolve(userId, homeOrg))
                .thenReturn(Set.of(Permission.MAIL_READ, Permission.FILE_UPLOAD));

        invoke(null);

        assertThat(seen.get().permissions())
                .containsExactlyInAnyOrder(Permission.MAIL_READ, Permission.FILE_UPLOAD);
        verify(permissionResolver).resolve(userId, homeOrg);
    }

    @Test
    void staffAuthorityComesFromTheUserRowNotTheToken() {
        // The token said platformAdmin=false, because identity has no idea and could not be believed
        // if it did. This database is where staff authority is granted, so it is where it is read.
        givenUser(homeOrg, true);
        when(activeOrganizations.resolve(userId, homeOrg)).thenReturn(homeOrg);
        when(memberships.existsActiveMembership(homeOrg, userId)).thenReturn(true);

        invoke(null);

        assertThat(seen.get().platformAdmin()).isTrue();
    }

    @Test
    void anAccountDeletedOnThePlatformIsNotAuthenticatedByAValidIdentityToken() {
        // Identity and the platform are separate stores, so a token can outlive the account it names.
        User deleted = user(homeOrg, false);
        deleted.setDeletedAt(Instant.now());
        when(users.findById(userId)).thenReturn(Optional.of(deleted));

        MockHttpServletResponse response = invoke(null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(seen.get()).isNull();
    }

    @Test
    void aMalformedOrganizationHeaderIsRejectedRatherThanIgnored() {
        // Ignoring it would silently serve the fallback tenant to a request that asked for a
        // different one, which is a confusing thing for a client bug to look like.
        givenUser(homeOrg, false);

        assertThat(invoke("not-a-uuid").getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------

    private void givenUser(UUID defaultOrg, boolean platformAdmin) {
        when(users.findById(userId)).thenReturn(Optional.of(user(defaultOrg, platformAdmin)));
    }

    private User user(UUID defaultOrg, boolean platformAdmin) {
        User user = new User();
        user.setId(userId);
        user.setEmail("someone@example.com");
        user.setDefaultOrganizationId(defaultOrg);
        user.setPlatformAdmin(platformAdmin);
        return user;
    }

    private MockHttpServletResponse invoke(String orgHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer identity-token");
        if (orgHeader != null) {
            request.addHeader(JwtAuthenticationFilter.ORG_HEADER, orgHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, capturing());
        } catch (Exception ex) {
            throw new AssertionError("the filter must answer the request, never propagate", ex);
        }
        return response;
    }
}
