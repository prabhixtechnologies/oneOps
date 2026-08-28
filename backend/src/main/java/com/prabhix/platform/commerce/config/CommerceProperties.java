package com.prabhix.platform.commerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "prabhix.commerce")
public record CommerceProperties(
        @DefaultValue("INR") String currency,
        @DefaultValue("PT24H") Duration downloadLinkTtl,
        @DefaultValue("5") int maxDownloadCount,
        @DefaultValue("P14D") Duration cartTtl,
        @DefaultValue("PT15M") Duration stockHoldTtl,
        @DefaultValue({"http://localhost:3000"}) List<String> allowedOrigins,
        @DefaultValue("60") int publicRateLimitPerMinute) {
}
