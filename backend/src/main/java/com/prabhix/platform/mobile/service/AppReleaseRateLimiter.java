package com.prabhix.platform.mobile.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mobile.config.MobileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppReleaseRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String PREFIX = "pbx:mobile:app-release:rl:";

    private final StringRedisTemplate redis;
    private final MobileProperties properties;

    /**
     * @return requests remaining in the current window after counting this call.
     */
    public int consume(String ipAddress) {
        int limit = properties.appReleaseRateLimitPerMinute();
        if (ipAddress == null || ipAddress.isBlank()) {
            return limit;
        }
        String key = PREFIX + ipAddress;
        try {
            Long counter = redis.opsForValue().increment(key);
            long used = counter == null ? 1L : counter;
            if (used == 1L) {
                redis.expire(key, WINDOW);
            }
            if (used > limit) {
                throw ApiException.of(ErrorCode.RATE_LIMITED, "Too many requests. Try again later.");
            }
            return (int) Math.max(0, limit - used);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("App release rate limiter unavailable, allowing request: {}", ex.getMessage());
            return limit;
        }
    }

    public long windowResetSeconds() {
        return WINDOW.toSeconds();
    }
}
