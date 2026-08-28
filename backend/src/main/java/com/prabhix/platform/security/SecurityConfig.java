package com.prabhix.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiError;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.filter.AccessLogFilter;
import com.prabhix.platform.observability.filter.CorrelationIdFilter;
import com.prabhix.platform.observability.filter.MdcPrincipalEnrichmentFilter;
import com.prabhix.platform.security.apikey.ApiKeyAuthenticationFilter;
import com.prabhix.platform.security.jwt.JwtAuthenticationFilter;
import com.prabhix.platform.security.ratelimit.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;

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

    /** Endpoints reachable without a token. Everything not listed requires authentication. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/magic-link/**",
            "/api/v1/auth/otp/**",
            "/api/v1/auth/sso/**",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/email/verify/confirm",
            "/api/v1/auth/invites/*/preview",
            "/api/v1/site/**",
            "/api/v1/visitor/public/**",
            "/api/v1/chat/public/**",
            "/api/v1/commerce/public/**",
            "/api/v1/mail/t/**",
            "/api/v1/billing/webhooks/**",
            "/api/v1/commerce/webhooks/**",
            "/api/v1/mail/webhooks/**",
            "/api/v1/mail/inbound/**",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No cookies are used for API auth, so there is no CSRF surface to protect.
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
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasAuthority("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, ErrorCode.UNAUTHENTICATED,
                                        "Authentication is required", request.getRequestURI()))
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
        return new BCryptPasswordEncoder(properties.security().password().bcryptStrength());
    }

    /**
     * Registered so Spring Security has a provider for form-style checks, but the primary
     * path is {@code AuthService}, which verifies credentials directly to control lockout
     * and audit behaviour.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                           PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
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
