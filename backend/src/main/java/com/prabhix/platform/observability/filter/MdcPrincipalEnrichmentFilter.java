package com.prabhix.platform.observability.filter;

import com.prabhix.platform.observability.context.MdcKeys;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enriches MDC with principal fields after JWT authentication has run.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class MdcPrincipalEnrichmentFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PrabhixPrincipal principal) {
            org.slf4j.MDC.put(MdcKeys.USER_ID, principal.userId().toString());
            if (principal.sessionId() != null) {
                org.slf4j.MDC.put(MdcKeys.SESSION_ID, principal.sessionId().toString());
            }
            if (principal.organizationId() != null) {
                org.slf4j.MDC.put(MdcKeys.ORG_ID, principal.organizationId().toString());
            }
        } else {
            TenantContext.current().ifPresent(orgId ->
                    org.slf4j.MDC.put(MdcKeys.ORG_ID, orgId.toString()));
        }
        chain.doFilter(request, response);
    }
}
