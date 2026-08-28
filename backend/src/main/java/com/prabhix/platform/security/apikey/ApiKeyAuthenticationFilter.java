package com.prabhix.platform.security.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiError;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.org.service.ApiKeyService;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

/**
 * Authenticates machine callers via org-scoped API keys before the JWT filter runs.
 *
 * <p>Keys may be sent as {@code X-API-Key} or as {@code Authorization: Bearer pbx_live_…}.
 * When the bearer form is used, the Authorization header is hidden from downstream filters
 * so the JWT filter does not attempt to parse the opaque key as a JWT.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawKey = extractApiKey(request);
        if (rawKey == null) {
            chain.doFilter(request, response);
            return;
        }

        boolean bearerKey = isBearerApiKey(request);
        try {
            PrabhixPrincipal principal = apiKeyAuthenticationService.authenticate(rawKey);

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.authorities());
            authentication.setDetails(request.getRemoteAddr());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            TenantContext.set(principal.organizationId());

            HttpServletRequest effective = bearerKey
                    ? new AuthorizationStrippingRequest(request)
                    : request;
            chain.doFilter(effective, response);
        } catch (ApiException ex) {
            writeError(request, response, ex);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private String extractApiKey(HttpServletRequest request) {
        String dedicated = request.getHeader(API_KEY_HEADER);
        if (dedicated != null && !dedicated.isBlank()) {
            return dedicated.trim();
        }
        String bearer = bearerToken(request);
        if (bearer != null && bearer.startsWith(ApiKeyService.KEY_PREFIX)) {
            return bearer;
        }
        return null;
    }

    private boolean isBearerApiKey(HttpServletRequest request) {
        String bearer = bearerToken(request);
        return bearer != null && bearer.startsWith(ApiKeyService.KEY_PREFIX);
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
        response.setStatus(ex.getCode().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError body = new ApiError(ex.getCode().name(), ex.getMessage(), null,
                null, request.getRequestURI(), Instant.now());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static final class AuthorizationStrippingRequest extends HttpServletRequestWrapper {

        AuthorizationStrippingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (AUTH_HEADER.equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            if (AUTH_HEADER.equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }
    }
}
