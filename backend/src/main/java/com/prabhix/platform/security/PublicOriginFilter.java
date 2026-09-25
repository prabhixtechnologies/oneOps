package com.prabhix.platform.security;

import com.prabhix.platform.chat.config.ChatProperties;
import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.visitor.config.VisitorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Enforces {@code prabhix.visitor.allowed-origins} and {@code prabhix.chat.allowed-origins}
 * on public ingest endpoints, independent of the global CORS configuration.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class PublicOriginFilter extends OncePerRequestFilter {

    private final VisitorProperties visitorProperties;
    private final ChatProperties chatProperties;
    private final CommerceProperties commerceProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/oneops/visitor/public/")
                && !path.startsWith("/api/v1/oneops/chat/public/")
                && !path.startsWith("/api/v1/oneops/commerce/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        List<String> allowed = allowedOriginsFor(request.getRequestURI());

        if (allowed.stream().anyMatch(origin::equalsIgnoreCase)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"code":"FORBIDDEN","message":"Origin is not allowed for this endpoint"}
                """);
    }

    private List<String> allowedOriginsFor(String path) {
        if (path.startsWith("/api/v1/oneops/chat/public/")) {
            return chatProperties.allowedOrigins();
        }
        if (path.startsWith("/api/v1/oneops/commerce/public/")) {
            return commerceProperties.allowedOrigins();
        }
        return visitorProperties.allowedOrigins();
    }
}
