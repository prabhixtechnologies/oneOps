package com.prabhix.platform.visitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "prabhix.visitor")
public record VisitorProperties(
        @DefaultValue("P90D") Duration rawRetention,
        @DefaultValue("500") int ingestBatchMaxEvents,
        @DefaultValue("65536") int ingestMaxPayloadBytes,
        @DefaultValue("120") int presenceTtlSeconds,
        @DefaultValue("60") int ingestRateLimitPerMinute,
        @DefaultValue("120") int ingestRateLimitPerVisitorPerMinute,
        @DefaultValue({"http://localhost:3000"}) List<String> allowedOrigins,
        @DefaultValue("500") int retentionBatchSize) {
}
