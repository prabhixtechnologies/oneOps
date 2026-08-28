package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.config.CommerceProperties;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommerceRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String PREFIX = "pbx:commerce:rl:";

    private final StringRedisTemplate redis;
    private final CommerceProperties properties;

    public void checkIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return;
        }
        check(PREFIX + "ip:" + ipAddress, properties.publicRateLimitPerMinute());
    }

    private void check(String key, int limit) {
        try {
            Long counter = redis.opsForValue().increment(key);
            long used = counter == null ? 1L : counter;
            if (used == 1L) {
                redis.expire(key, WINDOW);
            }
            if (used > limit) {
                throw ApiException.of(ErrorCode.RATE_LIMITED, "Too many requests. Try again later.");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Commerce rate limiter unavailable, allowing request: {}", ex.getMessage());
        }
    }
}
