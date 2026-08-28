package com.prabhix.platform.visitor.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.visitor.config.VisitorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorRateLimiterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private VisitorRateLimiter limiter;

    @BeforeEach
    void setUp() {
        VisitorProperties props = new VisitorProperties(
                Duration.ofDays(90), 50, 65536, 120, 2, 5, List.of("http://localhost:3000"), 500);
        limiter = new VisitorRateLimiter(redis, props);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void allowsRequestsUnderLimit() {
        when(valueOps.increment(any())).thenReturn(1L);
        limiter.checkIp("1.2.3.4");
        verify(redis).expire(any(), eq(Duration.ofMinutes(1)));
    }

    @Test
    void rejectsWhenLimitExceeded() {
        when(valueOps.increment(any())).thenReturn(3L);
        ApiException ex = assertThrows(ApiException.class, () -> limiter.checkIp("1.2.3.4"));
        assertEquals(ErrorCode.RATE_LIMITED, ex.getCode());
    }
}
