package com.prabhix.platform.site.service;

import com.prabhix.platform.security.ratelimit.RateLimitKeyHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Hourly caps on newsletter subscribe attempts. Exceeding a limit returns {@code limited=true} so callers
 * can respond with a generic acknowledgement instead of revealing which key tripped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteSubscribeRateLimiter {

    static final int PER_EMAIL_PER_HOUR = 5;
    static final int PER_IP_PER_HOUR = 30;

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final String PREFIX = "pbx:site:subscribe:";

    private final StringRedisTemplate redis;

    /**
     * @return {@code true} when either limit was exceeded and the subscribe should short-circuit with a
     *     generic ack.
     */
    public boolean isLimited(String normalizedEmail, String clientIp) {
        try {
            if (normalizedEmail != null && !normalizedEmail.isBlank()) {
                if (exceeded(PREFIX + "email:" + RateLimitKeyHasher.hashToken(normalizedEmail), PER_EMAIL_PER_HOUR)) {
                    return true;
                }
            }
            if (clientIp != null && !clientIp.isBlank()) {
                if (exceeded(PREFIX + "ip:" + clientIp, PER_IP_PER_HOUR)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            log.warn("Newsletter rate limiter unavailable, allowing request: {}", ex.getMessage());
            return false;
        }
    }

    private boolean exceeded(String key, int limit) {
        Long counter = redis.opsForValue().increment(key);
        long used = counter == null ? 1L : counter;
        if (used == 1L) {
            redis.expire(key, WINDOW);
        }
        return used > limit;
    }
}
