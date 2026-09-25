package com.prabhix.platform.security;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiError;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.filter.AccessLogFilter;
import com.prabhix.platform.observability.filter.CorrelationIdFilter;
import com.prabhix.platform.observability.filter.MdcPrincipalEnrichmentFilter;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.security.apikey.ApiKeyAuthenticationFilter;
import com.prabhix.platform.security.jwt.JwtAuthenticationFilter;
import com.prabhix.platform.security.ratelimit.RateLimitFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final MdcPrincipalEnrichmentFilter mdcPrincipalEnrichmentFilter;
    private final AccessLogFilter accessLogFilter;
    private final PrabhixProperties properties;
    private final ObjectMapper objectMapper;
    // Resolved per request rather than injected directly: the logger sits on top of JPA, and this
    // configuration is built early enough that a hard dependency risks an initialisation cycle.
    private final ObjectProvider<StructuredEventLogger> eventLoggerProvider;

    /** Endpoints reachable without a token. Everything not listed requires authentication. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/oneops/auth/login",
            "/api/v1/oneops/auth/register",
            "/api/v1/oneops/auth/refresh",
            // The browser session cookie is the credential here, so there is no bearer token to
            // check. Listing it as public is what lets a tab that has been open past its access
            // token's lifetime get a new one instead of bouncing the person to the login page.
            "/api/v1/oneops/auth/session/token",
            "/api/v1/oneops/auth/magic-link/**",
            "/api/v1/oneops/auth/otp/**",
            "/api/v1/oneops/auth/sso/**",
            "/api/v1/oneops/auth/password/forgot",
            "/api/v1/oneops/auth/password/reset",
            "/api/v1/oneops/auth/email/verify/confirm",
            "/api/v1/oneops/auth/invites/preview",
            "/api/v1/oneops/site/**",
            "/api/v1/oneops/visitor/public/**",
            "/api/v1/oneops/chat/public/**",
            "/api/v1/oneops/commerce/public/**",
            "/api/v1/oneops/mail/t/**",
            "/api/v1/oneops/billing/webhooks/**",
            "/api/v1/oneops/commerce/webhooks/**",
            "/api/v1/oneops/mail/webhooks/**",
            "/api/v1/oneops/mail/inbound/**",
            // Service-to-service, and guarded by a shared token checked inside the controller rather
            // than by a bearer token, because the caller is identity rather than a person and holds no
            // account to authenticate as. Listed here so the filter does not demand one.
            //
            // Public only in the sense of "not behind the JWT filter": Caddy answers /internal with a
            // 404 at the edge, so nothing off this network can reach it at all. Two locks, because this
            // one creates organizations and grants their ownership.
            "/internal/**",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Left disabled after the shared session cookie was introduced, which is worth
                // justifying rather than inheriting.
                //
                // Exactly one endpoint reads a cookie: POST /auth/session/token. Every other
                // endpoint authenticates from an Authorization header or an API key, and a header
                // is not something another origin's page can make the browser attach — that is the
                // property CSRF tokens exist to recreate for cookie auth.
                //
                // The cookie is SameSite=Lax, so it is withheld from cross-site POSTs; a form on
                // another site posting here therefore arrives with no credential and gets a 401.
                // And the response is useless to an attacker's page regardless: reading it requires
                // an Access-Control-Allow-Origin naming that origin, and the allow-list below names
                // only our own hosts.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        // Authorize the REQUEST dispatch only. Since Spring Security 6 this filter
                        // runs on every dispatch type, but our authentication filters extend
                        // OncePerRequestFilter, which by default skips ASYNC — so on the async
                        // dispatch there is no Authentication left and the request is denied.
                        //
                        // That silently broke every SSE endpoint (mail, chat and AI streams return
                        // SseEmitter). The emitter commits the response, the async dispatch is then
                        // refused, and Spring cannot even write the 403 because the headers have
                        // gone; the client receives no bytes, reconnects five seconds later, and
                        // the server logs a stack trace each time. Realtime updates never arrived.
                        //
                        // Permitting these dispatches is safe: both are continuations of a request
                        // the container already ran through the REQUEST dispatch, where the rules
                        // below applied in full. ERROR is included for the same reason, so a failed
                        // request renders its error body instead of turning into a second denial.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/oneops/admin/**").hasAuthority("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            recordUnauthenticated(request);
                            writeError(response, ErrorCode.UNAUTHENTICATED,
                                    "Authentication is required", request.getRequestURI());
                        })
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, ErrorCode.PERMISSION_DENIED,
                                        "You do not have permission to perform this action",
                                        request.getRequestURI())))
                // Order of these calls matters as much as the resulting chain order:
                // addFilterBefore/After can only reference a filter class whose order is
                // already registered, and each call registers the filter it adds. So
                // RateLimitFilter has to be placed before anything can be positioned
                // relative to it. Resulting execution order is correlation id, rate limit,
                // JWT, MDC principal, access log — with the access log innermost so the
                // principal is still in MDC when it writes.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(correlationIdFilter, RateLimitFilter.class)
                .addFilterAfter(apiKeyAuthenticationFilter, RateLimitFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, ApiKeyAuthenticationFilter.class)
                .addFilterAfter(mdcPrincipalEnrichmentFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(accessLogFilter, MdcPrincipalEnrichmentFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Explicit origins, never "*": the console sends an Authorization header, and
        // wildcard origins cannot be combined with credentialed requests.
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin",
                JwtAuthenticationFilter.ORG_HEADER, ApiKeyAuthenticationFilter.API_KEY_HEADER,
                "X-Prabhix-Device", "X-Requested-With",
                "Idempotency-Key", CorrelationIdFilter.REQUEST_ID_HEADER,
                CorrelationIdFilter.CORRELATION_HEADER));
        configuration.setExposedHeaders(List.of(
                "X-RateLimit-Remaining", "X-RateLimit-Reset",
                CorrelationIdFilter.CORRELATION_HEADER, CorrelationIdFilter.REQUEST_ID_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Records a rejection at the security boundary.
     *
     * <p>Until this existed such a request left no trace anywhere: the access log skips
     * {@code /auth/**} as sensitive, and this entry point writes the response itself rather than
     * raising through {@code GlobalExceptionHandler}. A client that failed to authenticate was
     * therefore invisible, which made "it says authentication is required" impossible to
     * diagnose from the server side.
     *
     * <p>Only persisted when a credential was actually presented. A public host takes a constant
     * stream of anonymous probes for things like {@code /actuator/env}, and a row for each would
     * bury the case worth reading: a token or API key that was sent and refused.
     */
    private void recordUnauthenticated(HttpServletRequest request) {
        StructuredEventLogger eventLogger = eventLoggerProvider.getIfAvailable();
        if (eventLogger == null) {
            return;
        }
        boolean credentialsPresented = request.getHeader("Authorization") != null
                || request.getHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER) != null;
        eventLogger.log(LogEventCode.AUTH_REQUEST_UNAUTHENTICATED,
                Map.of("path", request.getRequestURI(),
                        "method", request.getMethod(),
                        "credentialsPresented", credentialsPresented),
                credentialsPresented);
    }

    private void writeError(HttpServletResponse response,
                            ErrorCode code,
                            String message,
                            String path) throws java.io.IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(code.name(), message, null, null, path, Instant.now()));
    }
}
