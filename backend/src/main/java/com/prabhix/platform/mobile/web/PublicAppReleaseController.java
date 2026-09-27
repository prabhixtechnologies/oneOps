package com.prabhix.platform.mobile.web;

import com.prabhix.platform.mobile.dto.AppReleaseDtos.AppReleaseResponse;
import com.prabhix.platform.mobile.service.AppReleaseRateLimiter;
import com.prabhix.platform.mobile.service.AppReleaseService;
import com.prabhix.platform.security.TrustedClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/oneops/public")
@RequiredArgsConstructor
public class PublicAppReleaseController {

    private final AppReleaseService appReleaseService;
    private final AppReleaseRateLimiter rateLimiter;
    private final TrustedClientIpResolver clientIpResolver;

    @GetMapping("/app-release")
    public ResponseEntity<AppReleaseResponse> appRelease(
            @RequestParam String app,
            @RequestParam String platform,
            @RequestParam(name = "build", defaultValue = "0") int build,
            HttpServletRequest request) {
        int remaining = rateLimiter.consume(clientIpResolver.resolve(request));
        AppReleaseResponse body = appReleaseService.evaluate(app, platform, build);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(appReleaseService.cacheMaxAgeSeconds(), TimeUnit.SECONDS)
                        .cachePublic()
                        .mustRevalidate())
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .header("X-RateLimit-Reset", String.valueOf(rateLimiter.windowResetSeconds()))
                .body(body);
    }
}
