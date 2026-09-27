package com.prabhix.platform.security.ratelimit;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiError;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.security.TrustedClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Fixed-window rate limiting in Redis, keyed per client.
 *
 * <p>Auth endpoints get a much tighter budget than the rest of the API because they are what
 * credential-stuffing targets. The window is fixed rather than sliding: it allows a burst at
 * a boundary, but costs one {@code INCR} instead of sorted-set bookkeeping on every request,
 * which matters when this runs ahead of every call.
 *
 * <p>Fails open if Redis is down. A rate limiter that takes the API with it is worse than no
 * rate limiter.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "pbx:rl:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * Auth paths that get the ordinary API budget instead of the credential-stuffing one.
     *
     * <p>The tight budget exists to slow an attacker guessing secrets. The session exchange carries
     * no guessable secret: its credential is an HttpOnly cookie the caller can neither read nor
     * construct, so repeating the call proves nothing an attacker does not already have.
     *
     * <p>It is also routine — every console tab calls it on load and again when its access token
     * ages out, and with two consoles on one browser that adds up fast. Sharing ten attempts a
     * minute with sign-in meant an ordinary morning of reloading could exhaust the budget, and a 429
     * here is indistinguishable from being signed out.
     */
    private static final Set<String> ROUTINE_AUTH_PATHS = Set.of("/api/v1/oneops/auth/session/token");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final PrabhixProperties.Security.RateLimit config;
    private final TrustedClientIpResolver clientIpResolver;
    private final java.util.concurrent.ConcurrentHashMap<String, WindowCounter> localFallback =
            new java.util.concurrent.ConcurrentHashMap<>();

    public RateLimitFilter(StringRedisTemplate redis,
                           ObjectMapper objectMapper,
                           PrabhixProperties properties,
                           TrustedClientIpResolver clientIpResolver) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.config = properties.security().rateLimit();
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !config.enabled()
                || path.startsWith("/actuator/health")
                // Gateway callbacks must never be throttled: Razorpay retries are finite and
                // dropping one loses a payment state transition.
                || path.startsWith("/api/v1/oneops/billing/webhooks")
                || path.startsWith("/api/v1/oneops/commerce/webhooks");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean authEndpoint = path.startsWith("/api/v1/oneops/auth/") && !ROUTINE_AUTH_PATHS.contains(path);
        int limit = authEndpoint ? config.authAttemptsPerMinute() : config.apiRequestsPerMinute();
        String key = KEY_PREFIX + (authEndpoint ? "auth:" : "api:") + clientKey(request);

        long used;
        try {
            Long counter = redis.opsForValue().increment(key);
            used = counter == null ? 1L : counter;
            if (used == 1L) {
                redis.expire(key, WINDOW);
            }
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, using in-memory fallback: {}", ex.getMessage());
            used = localFallback
                    .computeIfAbsent(key, ignored -> new WindowCounter())
                    .increment();
            long fallbackRemaining = Math.max(0, limit - used);
            response.setHeader("X-RateLimit-Remaining", String.valueOf(fallbackRemaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(WINDOW.toSeconds()));
            if (used > limit) {
                writeRateLimited(request, response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        long remaining = Math.max(0, limit - used);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(WINDOW.toSeconds()));

        if (used > limit) {
            log.info("Rate limit hit on {} for {}", request.getRequestURI(), key);
            writeRateLimited(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Prefers the authenticated subject so one abusive user behind shared NAT does not
     * throttle their colleagues. Falls back to the client IP for anonymous calls.
     */
    private String clientKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            return "t" + RateLimitKeyHasher.hashToken(token);
        }
        return "ip" + clientIpResolver.resolve(request);
    }

    private void writeRateLimited(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                ErrorCode.RATE_LIMITED.name(),
                "Too many requests. Wait a moment and try again.",
                null, null, request.getRequestURI(), Instant.now()));
    }

    private static final class WindowCounter {
        private long windowStartMs = System.currentTimeMillis();
        private long count;

        synchronized long increment() {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= WINDOW.toMillis()) {
                windowStartMs = now;
                count = 0;
            }
            return ++count;
        }
    }
}
