package com.prabhix.platform.mobile.web;

import com.prabhix.platform.mobile.dto.AppReleaseDtos.AppReleaseResponse;
import com.prabhix.platform.mobile.service.AppReleaseRateLimiter;
import com.prabhix.platform.mobile.service.AppReleaseService;
import com.prabhix.platform.security.TrustedClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicAppReleaseControllerTest {

    @Mock private AppReleaseService appReleaseService;
    @Mock private AppReleaseRateLimiter rateLimiter;
    @Mock private TrustedClientIpResolver clientIpResolver;

    private PublicAppReleaseController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicAppReleaseController(appReleaseService, rateLimiter, clientIpResolver);
    }

    @Test
    void setsCacheAndRateLimitHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/oneops/public/app-release");
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(rateLimiter.consume("203.0.113.10")).thenReturn(119);
        when(rateLimiter.windowResetSeconds()).thenReturn(60L);
        when(appReleaseService.cacheMaxAgeSeconds()).thenReturn(300);
        when(appReleaseService.evaluate("oneops", "ANDROID", 8))
                .thenReturn(new AppReleaseResponse("ANDROID", 1, 10, false, true, null, null));

        var response = controller.appRelease("oneops", "ANDROID", 8, request);

        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getCacheControl()).isNotNull();
        assertThat(headers.getFirst("Cache-Control")).contains("max-age=300");
        assertThat(headers.getFirst("X-RateLimit-Remaining")).isEqualTo("119");
        assertThat(headers.getFirst("X-RateLimit-Reset")).isEqualTo("60");
        assertThat(response.getBody().updateRequired()).isTrue();
    }
}
