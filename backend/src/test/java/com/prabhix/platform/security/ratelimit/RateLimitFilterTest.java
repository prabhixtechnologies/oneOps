package com.prabhix.platform.security.ratelimit;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which budget each path draws from.
 *
 * <p>The distinction matters because the two budgets differ by a factor of sixty, and spending from
 * the wrong one is invisible until a real user is refused.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final int AUTH_LIMIT = 10;
    private static final int API_LIMIT = 600;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RateLimitFilter filter;

    // The refusal body carries an Instant, which a bare mapper cannot write. Spring's injected
    // mapper has the JSR-310 module registered, so register it here rather than have the test fail
    // on something production does not do.
    private final ObjectMapper objectMapper = new ObjectMapper()
            ;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        PrabhixProperties properties = TestProperties.withSecurity(
                new PrabhixProperties.Security(
                        TestProperties.security(java.time.Duration.ofMinutes(15)).jwt(),
                        TestProperties.identityDisabled(),
                        new PrabhixProperties.Security.RateLimit(true, AUTH_LIMIT, API_LIMIT),
                        null,
                        TestProperties.sessionCookie()));
        filter = new RateLimitFilter(redis, objectMapper, properties);
    }

    /** Sends a request that has already been made {@code priorCalls} times in this window. */
    private MockHttpServletResponse callAfter(String path, long priorCalls) throws Exception {
        when(valueOps.increment(anyString())).thenReturn(priorCalls + 1);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    private long remaining(MockHttpServletResponse response) {
        return Long.parseLong(response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("signing in draws from the tight credential-stuffing budget")
    void loginUsesTheAuthBudget() throws Exception {
        MockHttpServletResponse response = callAfter("/api/v1/auth/login", 0);

        assertThat(remaining(response)).isEqualTo(AUTH_LIMIT - 1);
    }

    @Test
    @DisplayName("an eleventh sign-in attempt in a minute is refused")
    void loginIsRefusedOnceTheBudgetIsSpent() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        when(valueOps.increment(anyString())).thenReturn((long) AUTH_LIMIT + 1);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("the session exchange draws from the ordinary API budget, not the sign-in one")
    void sessionExchangeUsesTheApiBudget() throws Exception {
        // It is a routine call: every console tab makes it on load and again when its access token
        // ages out. Charged against ten attempts a minute, two consoles and a few reloads would
        // exhaust it, and the resulting 429 is indistinguishable from having been signed out.
        MockHttpServletResponse response = callAfter("/api/v1/auth/session/token", 0);

        assertThat(remaining(response)).isEqualTo(API_LIMIT - 1);
    }

    @Test
    @DisplayName("the session exchange still survives a burst that would lock out sign-in")
    void sessionExchangeSurvivesABurst() throws Exception {
        MockHttpServletResponse response = callAfter("/api/v1/auth/session/token", AUTH_LIMIT + 5);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(remaining(response)).isEqualTo(API_LIMIT - AUTH_LIMIT - 6);
    }

    @Test
    @DisplayName("the session exchange is still bounded, so it cannot be hammered indefinitely")
    void sessionExchangeIsStillLimited() throws Exception {
        MockHttpServletResponse response = callAfter("/api/v1/auth/session/token", API_LIMIT);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("refresh keeps the tight budget, since a refresh token is a guessable secret")
    void refreshKeepsTheAuthBudget() throws Exception {
        MockHttpServletResponse response = callAfter("/api/v1/auth/refresh", 0);

        assertThat(remaining(response)).isEqualTo(AUTH_LIMIT - 1);
    }

    @Test
    @DisplayName("health checks are not counted at all")
    void healthIsExempt() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/readiness");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
