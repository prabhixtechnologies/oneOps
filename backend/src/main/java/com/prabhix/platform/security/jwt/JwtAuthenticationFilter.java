package com.prabhix.platform.security.jwt;

import com.prabhix.identity.client.BearerTokens;
import com.prabhix.identity.client.IdentityClientException;
import com.prabhix.identity.client.IdentityToken;
import com.prabhix.identity.client.IdentityTokenException;
import com.prabhix.identity.client.IdentityTokenVerifier;
import com.prabhix.identity.client.IdentityUserMirror;
import com.prabhix.platform.common.error.ApiError;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.service.ActiveOrganizationResolver;
import com.prabhix.platform.org.service.PermissionResolver;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.service.PlatformStaffService;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.tenant.ImpersonationAuditor;
import com.prabhix.platform.security.tenant.TenantContext;
import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticates the caller from the {@code Authorization: Bearer} header and establishes the
 * tenant for the request.
 *
 * <p>The only tokens accepted are those Prabhix Identity issued, verified RS256 against its
 * published keys. There is no other path: nothing here holds a secret that could verify — and
 * therefore mint — a token, which is the property the split into an identity service exists for.
 *
 * <p>An identity token says who the caller is and nothing about what they may do. The organization
 * comes from {@code X-Prabhix-Org} or the caller's default, the permissions from this database for
 * that pairing, and staff authority from the local user row. Anything the header names that the
 * caller has no claim to is a cross-tenant attempt and is rejected, not silently downgraded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ORG_HEADER = "X-Prabhix-Org";

    private final IdentityTokenVerifier verifier;
    private final TokenDenyList denyList;
    private final ObjectMapper objectMapper;
    private final StructuredEventLogger eventLogger;
    private final ImpersonationAuditor impersonationAuditor;
    private final PermissionResolver permissionResolver;
    private final ActiveOrganizationResolver activeOrganizations;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final IdentityUserMirror identityUserMirror;
    private final PlatformStaffService platformStaff;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = BearerTokens.from(request);
        if (token == null) {
            // No credentials is not an error here. Public endpoints proceed; protected ones
            // are rejected later by the authorization rules.
            chain.doFilter(request, response);
            return;
        }

        try {
            IdentityToken identity = verify(token);

            if (denyList.isRevoked(identity)) {
                throw ApiException.of(ErrorCode.TOKEN_REVOKED,
                        "This session was signed out. Sign in again.");
            }

            PrabhixPrincipal effective = authorizeIdentityToken(identity, request);

            var authentication = new UsernamePasswordAuthenticationToken(
                    effective, null, effective.authorities());
            authentication.setDetails(request.getRemoteAddr());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (effective.hasOrganization()) {
                TenantContext.set(effective.organizationId());
            }

            // Deliberately after both contexts are established, so the event is attributed to the
            // organization being viewed and carries the admin as its actor. Recording it earlier
            // would file it against the admin's own organization, where nobody would look for it.
            // The token itself names no organization, so the "from" side is always empty.
            if (effective.platformAdmin()) {
                impersonationAuditor.recordAccess(effective.userId(), effective.sessionId(),
                        null, effective.organizationId());
            }

            chain.doFilter(request, response);
        } catch (ApiException ex) {
            // Written directly rather than rethrown: @RestControllerAdvice does not see
            // exceptions thrown in the filter chain.
            writeError(request, response, ex);
        } finally {
            // Must run even on the error path — these threads are pooled and reused.
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    /**
     * Turns the starter's refusal into this API's error vocabulary.
     *
     * <p>Expiry is the one reason worth telling apart, because a client answers it by refreshing
     * rather than by signing in again. Everything else — bad signature, unknown key, wrong issuer,
     * an HS256 token somebody minted with material they should not have — is one undifferentiated
     * "not valid", so a probe learns nothing about which check it failed.
     */
    private IdentityToken verify(String token) {
        try {
            return verifier.verify(token);
        } catch (IdentityTokenException ex) {
            throw switch (ex.reason()) {
                case EXPIRED -> ApiException.of(ErrorCode.TOKEN_EXPIRED, "Your session has expired");
                case INVALID -> ApiException.of(ErrorCode.TOKEN_INVALID, "That token is not valid");
                case UNTRUSTED -> ApiException.of(ErrorCode.UNAUTHENTICATED,
                        "This deployment does not trust an identity issuer");
            };
        }
    }

    /**
     * Builds authority for an identity token, which carries none of its own.
     *
     * <p>The organization comes from {@code X-Prabhix-Org} and is validated against membership, and
     * the permissions come from this database for that pairing. Nothing here is read from the token
     * beyond the subject, so a token cannot assert access it was not granted — and a role revoked a
     * second ago is gone on the next request rather than when the token happens to expire.
     *
     * <p>A request with no organization header falls back to the organization sign-in would have
     * picked: the remembered default if it is still an active membership, otherwise the only active
     * one. Somebody with several and no default resolves to none and is asked, which is the honest
     * answer — and an unscoped request is still allowed through, because {@code /users/me} and the
     * organization list have to work before anyone has chosen. Tenant-scoped rules refuse it anyway,
     * since the permission set for a null organization holds only platform-level grants.
     */
    private PrabhixPrincipal authorizeIdentityToken(IdentityToken token, HttpServletRequest request) {
        UUID userId = token.subject();

        // The mirror row. Absent means identity knows this person and this database has not been told
        // yet — the ordinary case for anyone who signed up after the bulk import — so it is fetched
        // once here rather than treated as a credential failure. Users who arrive through an invite
        // already have a row and never reach this.
        User user = userRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseGet(() -> {
                    mirror(userId);
                    return userRepository.findById(userId)
                            .filter(candidate -> !candidate.isDeleted())
                            .orElseThrow(() -> ApiException.of(ErrorCode.UNAUTHENTICATED,
                                    "This account is not provisioned on the platform"));
                });

        UUID requestedOrg = requestedOrganization(request);
        boolean platformAdmin = user.isPlatformAdmin();

        // Nothing named a tenant, so fall back to the one sign-in would have chosen. Without this the
        // first call a console makes after an identity sign-in — /auth/me, before it can possibly know
        // an organization to ask for — comes back with none, and the console has nothing to put in the
        // header on any later request. The header still wins whenever it is sent, so switching
        // organizations and staff impersonation are unaffected; this only answers the first question.
        if (requestedOrg == null) {
            requestedOrg = activeOrganizations.resolve(userId, user.getDefaultOrganizationId());
        }

        if (requestedOrg != null
                && !membershipRepository.existsActiveMembership(requestedOrg, userId)) {
            if (!platformAdmin) {
                log.warn("Cross-tenant attempt: user {} is not an active member of org {}",
                        userId, requestedOrg);
                throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS,
                        "You are not a member of that organization.");
            }
            // Staff reaching into a tenant they do not belong to. Allowed, but only for the roles whose
            // job involves tenant content: a billing hire has no reason to read a customer's mail.
            platformStaff.requireAny(userId, StaffRole.TENANT_ACCESS);
        }

        // Email and name from the local row rather than the token: the token has what identity knew
        // at issue, the row is refreshed whenever the mirror is, and a rename should not wait on expiry.
        return new PrabhixPrincipal(
                userId,
                user.getEmail(),
                user.effectiveDisplayName(),
                requestedOrg,
                permissionResolver.resolve(userId, requestedOrg),
                token.sessionId(),
                platformAdmin);
    }

    /**
     * Asks identity about a subject this database has never seen.
     *
     * <p>A deployment with no service token cannot ask, and an unknown subject is then refused,
     * because there is nobody to authorize. A subject identity signed a token for but will not
     * describe was deleted between issue and this request, and is refused the same way. Identity
     * being unreachable is not a credential failure and is reported as such.
     */
    private void mirror(UUID userId) {
        try {
            identityUserMirror.pull(userId);
        } catch (IdentityClientException ex) {
            throw switch (ex.kind()) {
                case NOT_FOUND -> ApiException.of(ErrorCode.UNAUTHENTICATED, "That account no longer exists");
                case DISABLED -> ApiException.of(ErrorCode.UNAUTHENTICATED,
                        "This account is not provisioned on the platform");
                case UNAVAILABLE, REJECTED -> {
                    log.error("Could not reach identity to mirror user {}: {}", userId, ex.getMessage());
                    yield ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                            "Could not verify your account right now. Try again.");
                }
            };
        }
    }

    private UUID requestedOrganization(HttpServletRequest request) {
        String requested = request.getHeader(ORG_HEADER);
        if (requested == null || requested.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(requested.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, ORG_HEADER + " is not a valid id");
        }
    }

    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            ApiException ex) throws IOException {
        // A credential was presented and refused. That is worth a row: it separates an expired or
        // revoked token from a client that simply never sent one, and this path writes the
        // response itself, so nothing else in the stack would ever record it.
        eventLogger.logNow(LogEventCode.AUTH_REQUEST_UNAUTHENTICATED,
                Map.of("path", request.getRequestURI(),
                        "method", request.getMethod(),
                        "credentialsPresented", true,
                        "errorCode", ex.getCode().name()));

        response.setStatus(ex.getCode().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError body = new ApiError(ex.getCode().name(), ex.getMessage(), null,
                null, request.getRequestURI(), Instant.now());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
