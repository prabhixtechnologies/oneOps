package com.prabhix.platform.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "prabhix.chat")
public record ChatProperties(
        @DefaultValue("PT24H") Duration conversationTokenTtl,
        @DefaultValue("30") int publicRateLimitPerMinute,
        @DefaultValue({"http://localhost:3000"}) List<String> allowedOrigins,
        @DefaultValue BusinessHoursDefaults businessHours) {

    public record BusinessHoursDefaults(
            @DefaultValue("09:00") String start,
            @DefaultValue("18:00") String end,
            @DefaultValue("Asia/Kolkata") String timezone) {
    }
}
