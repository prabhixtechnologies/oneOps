package com.prabhix.platform.mobile.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mobile.config.MobileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppReleaseRateLimiterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private AppReleaseRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AppReleaseRateLimiter(redis, new MobileProperties(
                300, 2,
                new MobileProperties.AppReleasePolicy(
                        new MobileProperties.PlatformRelease(1, 1, false, "", ""),
                        new MobileProperties.PlatformRelease(1, 1, false, "", "")),
                new MobileProperties.AppReleasePolicy(
                        new MobileProperties.PlatformRelease(1, 1, false, "", ""),
                        new MobileProperties.PlatformRelease(1, 1, false, "", "")),
                new MobileProperties.AppReleasePolicy(
                        new MobileProperties.PlatformRelease(1, 1, false, "", ""),
                        new MobileProperties.PlatformRelease(1, 1, false, "", ""))));
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void returnsRemainingWhenUnderLimit() {
        when(values.increment(anyString())).thenReturn(1L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        assertThat(limiter.consume("203.0.113.1")).isEqualTo(1);
    }

    @Test
    void rejectsWhenOverLimit() {
        when(values.increment(anyString())).thenReturn(3L);

        assertThatThrownBy(() -> limiter.consume("203.0.113.1"))
                .isInstanceOf(ApiException.class);
    }
}
