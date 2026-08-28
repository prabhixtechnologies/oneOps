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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rejects requests whose URL names an organization other than the caller's own.
 *
 * <p>Many endpoints take the organization from the path and pass it straight to a query.
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

    /** Path variables that always denote an organization. */
    private static final Set<String> ORGANIZATION_VARIABLES = Set.of("orgId", "organizationId");

    private static final String ORGANIZATION_ROUTE_PREFIX = "/api/v1/organizations/{id}";

    /**
     * Selecting a tenant is the one case where the path organization is supposed to differ from
     * the token's; {@code OrganizationSelectController} verifies membership itself.
     */
    private static final String SELECT_ROUTE = "/api/v1/organizations/{id}/select";

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object raw = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(raw instanceof Map)) {
            return true;
        }
        Map<String, String> variables = (Map<String, String>) raw;
        if (variables.isEmpty()) {
            return true;
        }

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
            assertMatches(variables.get(name), principal);
        }
        if (namesOrganizationById(request)) {
            assertMatches(variables.get("id"), principal);
        }
        return true;
    }

    private boolean namesOrganizationById(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String route)) {
            return false;
        }
        return route.startsWith(ORGANIZATION_ROUTE_PREFIX) && !route.equals(SELECT_ROUTE);
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
