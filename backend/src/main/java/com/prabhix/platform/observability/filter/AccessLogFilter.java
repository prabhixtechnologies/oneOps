package com.prabhix.platform.observability.filter;

import com.prabhix.platform.observability.config.ObservabilityProperties;
import com.prabhix.platform.observability.context.MdcKeys;
import com.prabhix.platform.observability.redaction.LogRedactor;
import com.prabhix.platform.observability.routing.RouteTemplateResolver;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Emits one structured access log per HTTP request with route template and duration.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    private final ObservabilityProperties properties;
    private final StructuredEventLogger eventLogger;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (shouldSkip(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (properties.sampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() > properties.sampleRate()) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            int status = response.getStatus();
            String route = RouteTemplateResolver.resolve(request);
            org.slf4j.MDC.put(MdcKeys.ROUTE_TEMPLATE, route);

            boolean slow = durationMs >= properties.slowRequestThreshold().toMillis();
            LogEventCode code = status >= 500
                    ? LogEventCode.HTTP_REQUEST_ERROR
                    : (slow ? LogEventCode.HTTP_REQUEST_SLOW : LogEventCode.HTTP_REQUEST_COMPLETED);

            Map<String, Object> payload = Map.of(
                    "method", request.getMethod(),
                    "route", route,
                    "status", status,
                    "durationMs", durationMs,
                    "responseBytes", declaredResponseSize(response),
                    "slow", slow);

            if (!LogRedactor.isSensitivePath(request.getRequestURI())) {
                eventLogger.log(code, payload, false);
            }
        }
    }

    /**
     * Read from the header rather than measured from the body on purpose. Wrapping the
     * response to count bytes would buffer it in memory, which stalls the SSE streams that
     * carry live chat and would hold whole file downloads on the heap. A missing
     * Content-Length (chunked or streamed) simply reports -1.
     */
    private long declaredResponseSize(HttpServletResponse response) {
        String declared = response.getHeader("Content-Length");
        if (declared == null || declared.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(declared.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/api/v1/oneops/event-logs")
                // Long-lived streams have no meaningful "completed" moment, and logging one
                // per open connection says nothing useful.
                || uri.endsWith("/stream")
                || uri.contains("/stream/")
                || uri.contains("visitor-stream");
    }
}
