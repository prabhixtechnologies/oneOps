package com.prabhix.platform.observability.filter;

import com.prabhix.platform.observability.context.CorrelationContext;
import com.prabhix.platform.observability.context.CorrelationIdSanitizer;
import com.prabhix.platform.observability.context.MdcKeys;
import com.prabhix.platform.security.PrabhixPrincipal;
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
 * Establishes correlation / request context in MDC before any other filter runs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String inbound = firstNonBlank(
                request.getHeader(CORRELATION_HEADER),
                request.getHeader(REQUEST_ID_HEADER));
        String correlationId = CorrelationIdSanitizer.resolve(inbound);
        String requestId = correlationId;

        try {
            org.slf4j.MDC.put(MdcKeys.CORRELATION_ID, correlationId);
            org.slf4j.MDC.put(MdcKeys.REQUEST_ID, requestId);
            org.slf4j.MDC.put(MdcKeys.TRACE_ID, correlationId);
            org.slf4j.MDC.put(MdcKeys.CLIENT_IP, clientIp(request));
            org.slf4j.MDC.put(MdcKeys.HTTP_METHOD, request.getMethod());
            org.slf4j.MDC.put(MdcKeys.HTTP_PATH, request.getRequestURI());
            org.slf4j.MDC.put(MdcKeys.USER_AGENT, truncate(request.getHeader("User-Agent"), 500));

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof PrabhixPrincipal principal) {
                org.slf4j.MDC.put(MdcKeys.USER_ID, principal.userId().toString());
                if (principal.sessionId() != null) {
                    org.slf4j.MDC.put(MdcKeys.SESSION_ID, principal.sessionId().toString());
                }
                if (principal.organizationId() != null) {
                    org.slf4j.MDC.put(MdcKeys.ORG_ID, principal.organizationId().toString());
                }
            }

            response.setHeader(CORRELATION_HEADER, correlationId);
            response.setHeader(REQUEST_ID_HEADER, requestId);

            chain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
