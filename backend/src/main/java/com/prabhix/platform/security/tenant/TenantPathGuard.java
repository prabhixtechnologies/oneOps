package com.prabhix.platform.security.tenant;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.security.PrabhixPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;
import java.util.UUID;

/**
 * Rejects requests whose query names an organization other than the caller's own.
 *
 * <p>Many endpoints take the organization from a query parameter and pass it straight to a query.
 * {@code @PreAuthorize} does not help: it checks that the caller holds a permission, and the
 * permissions on the token were granted within the caller's own tenant, so an owner of one
 * organization satisfies {@code ORG_UPDATE} while pointing at somebody else's.
 *
 * <p>The Hibernate tenant filter catches most of these, but only for
 * {@link com.prabhix.platform.common.entity.TenantScopedEntity} subclasses. {@code Organization}
 * itself is the tenant root and therefore unfiltered, so before this guard existed any signed-up
 * user could read and rename any other company by UUID. Checking centrally means a new
 * controller cannot reintroduce the hole by forgetting a check.
 */
@Component
public class TenantPathGuard implements HandlerInterceptor {

    /** Query parameters that always denote an organization. */
    private static final Set<String> ORGANIZATION_VARIABLES = Set.of("orgId", "organizationId");

    private static final String ORGANIZATION_ROUTE = "/api/v1/oneops/organizations";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof PrabhixPrincipal principal)) {
            return true;
        }
        // Staff tokens are deliberately cross-tenant; JwtAuthenticationFilter narrows them
        // through the X-Prabhix-Org header instead.
        if (principal.platformAdmin() || !principal.hasOrganization()) {
            return true;
        }

        for (String name : ORGANIZATION_VARIABLES) {
            assertMatches(request.getParameter(name), principal);
        }
        if (namesOrganizationById(request)) {
            assertMatches(request.getParameter("id"), principal);
        }
        return true;
    }

    /**
     * Item routes on {@code /organizations} use {@code ?id=}. Selecting a tenant is a different
     * path, and {@code OrganizationSelectController} verifies membership itself.
     */
    private boolean namesOrganizationById(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String route)) {
            return false;
        }
        int query = route.indexOf('?');
        if (query >= 0) {
            route = route.substring(0, query);
        }
        return ORGANIZATION_ROUTE.equals(route) && request.getParameter("id") != null;
    }

    private void assertMatches(String value, PrabhixPrincipal principal) {
        if (value == null || value.isBlank()) {
            return;
        }
        UUID target;
        try {
            target = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            // Not an identifier we can reason about; let the controller reject it.
            return;
        }
        if (!target.equals(principal.organizationId())) {
            throw ApiException.of(ErrorCode.CROSS_TENANT_ACCESS,
                    "That organization is not available on this session");
        }
    }
}
