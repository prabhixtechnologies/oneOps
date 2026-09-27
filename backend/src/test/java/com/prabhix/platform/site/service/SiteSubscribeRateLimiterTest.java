package com.prabhix.platform.site.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteSubscribeRateLimiterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private SiteSubscribeRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new SiteSubscribeRateLimiter(redis);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void limitsWhenEmailBucketExceeded() {
        when(values.increment(anyString())).thenReturn(6L);

        assertThat(limiter.isLimited("person@example.com", "203.0.113.1")).isTrue();
    }

    @Test
    void allowsUnderThreshold() {
        when(values.increment(anyString())).thenReturn(1L, 1L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        assertThat(limiter.isLimited("person@example.com", "203.0.113.1")).isFalse();
    }
}
