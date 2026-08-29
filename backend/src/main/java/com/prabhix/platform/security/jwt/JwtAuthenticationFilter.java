package com.prabhix.platform.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiError;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.tenant.ImpersonationAuditor;
import com.prabhix.platform.security.tenant.TenantContext;
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

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticates the caller from the {@code Authorization: Bearer} header and establishes the
 * tenant for the request.
 *
 * <p>Also honours {@code X-Prabhix-Org}: the console can ask to act in a different
 * organization than the token's default, but only one the token actually grants. Anything
 * else is a cross-tenant attempt and is rejected, not silently downgraded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ORG_HEADER = "X-Prabhix-Org";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final TokenDenyList denyList;
    private final ObjectMapper objectMapper;
    private final StructuredEventLogger eventLogger;
    private final ImpersonationAuditor impersonationAuditor;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null) {
            // No credentials is not an error here. Public endpoints proceed; protected ones
            // are rejected later by the authorization rules.
            chain.doFilter(request, response);
            return;
        }

        try {
            JwtService.ParsedToken parsed = jwtService.parseDetailed(token);
            PrabhixPrincipal principal = parsed.principal();

            if (denyList.isRevoked(principal.userId(), principal.sessionId(), parsed.issuedAt())) {
                throw ApiException.of(ErrorCode.TOKEN_REVOKED,
                        "This session was signed out. Sign in again.");
            }

            PrabhixPrincipal effective = applyRequestedOrganization(principal, request);

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
            if (effective.platformAdmin()) {
                impersonationAuditor.recordAccess(effective.userId(), effective.sessionId(),
                        principal.organizationId(), effective.organizationId());
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
     * Lets a multi-organization user act in a specific organization for this request.
     *
     * <p>For an ordinary user the header can only ever narrow to what the token already carries.
     * Honouring an arbitrary organization id would be a complete tenancy bypass, so a mismatch is a
     * hard failure and the header is never trusted as a source of permissions.
     */
    private PrabhixPrincipal applyRequestedOrganization(PrabhixPrincipal principal,
                                                        HttpServletRequest request) {
        String requested = request.getHeader(ORG_HEADER);
        if (requested == null || requested.isBlank()) {
            return principal;
        }

        UUID requestedOrg;
        try {
            requestedOrg = UUID.fromString(requested.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, ORG_HEADER + " is not a valid id");
        }

        if (principal.platformAdmin()) {
            // Staff may name any organization, membership or not, because support work requires it.
            // The permission set is deliberately left as the token's own: this grants a view into
            // another tenant's data, never the roles that tenant's own members hold. The access is
            // recorded by ImpersonationAuditor once the tenant context is in place.
            return new PrabhixPrincipal(principal.userId(), principal.email(), principal.displayName(),
                    requestedOrg, principal.permissions(), principal.sessionId(), true);
        }

        if (!requestedOrg.equals(principal.organizationId())) {
            log.warn("Cross-tenant attempt: user {} holds org {} but requested {}",
                    principal.userId(), principal.organizationId(), requestedOrg);
            throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS,
                    "Your session is not active for that organization. Switch organization and retry.");
        }

        return principal;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
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
