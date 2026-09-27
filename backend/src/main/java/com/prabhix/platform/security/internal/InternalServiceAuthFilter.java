package com.prabhix.platform.security.internal;

import com.prabhix.identity.client.ServiceTokenGuard;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
/**
 * Dedicated service-token gate for {@code /internal/**} with failure audit and rate limits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@RequiredArgsConstructor
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "pbx:internal-auth-fail:";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int FAIL_LIMIT = 30;

    private final ServiceTokenGuard serviceToken;
    private final StringRedisTemplate redis;
    private final StructuredEventLogger eventLogger;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!serviceToken.configured()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"code":"FORBIDDEN","message":"Service provisioning is not configured"}
                    """);
            return;
        }
        if (!serviceToken.permits(request)) {
            recordFailure(request);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"code":"FORBIDDEN","message":"Invalid service token"}
                    """);
            return;
        }
        chain.doFilter(request, response);
    }

    private void recordFailure(HttpServletRequest request) {
        String key = KEY_PREFIX + request.getRemoteAddr();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            if (count != null && count > FAIL_LIMIT) {
                eventLogger.logNow(LogEventCode.AUTH_REQUEST_UNAUTHENTICATED, Map.of(
                        "path", request.getRequestURI(),
                        "reason", "internal_service_token_rate_limited"));
            }
        } catch (RuntimeException ex) {
            eventLogger.logNow(LogEventCode.AUTH_REQUEST_UNAUTHENTICATED, Map.of(
                    "path", request.getRequestURI(),
                    "reason", "invalid_internal_service_token"));
        }
    }
}
