package com.prabhix.platform.observability.routing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves a low-cardinality route template for access logs instead of raw URIs.
 */
public final class RouteTemplateResolver {

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("/\\d+");

    private RouteTemplateResolver() {
    }

    @SuppressWarnings("unchecked")
    public static String resolve(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (attribute instanceof String pattern && !pattern.isBlank()) {
            return pattern;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        String templated = UUID_SEGMENT.matcher(uri).replaceAll("/{id}");
        templated = NUMERIC_SEGMENT.matcher(templated).replaceAll("/{id}");
        return templated;
    }

    public static String resolveFromPathVariables(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map && !map.isEmpty()) {
            return resolve(request);
        }
        return resolve(request);
    }
}
