package com.prabhix.platform.observability.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "prabhix.observability")
public record ObservabilityProperties(
        @NotBlank @DefaultValue("console") String logFormat,
        @DefaultValue("P90D") Duration eventLogRetention,
        @DefaultValue("0 45 2 * * *") String eventLogRetentionCron,
        @DefaultValue("PT1S") Duration slowRequestThreshold,
        @DefaultValue("1.0") @DecimalMin("0.0") @DecimalMax("1.0") double sampleRate) {

    /**
     * {@code logback-spring.xml} selects its appender by substituting this value into an
     * {@code appender-ref}, so a typo would leave the application with no appenders at all —
     * silently, and with no log line to explain it. Refusing to start is far kinder.
     */
    public ObservabilityProperties {
        if (logFormat != null && !"console".equalsIgnoreCase(logFormat)
                && !"json".equalsIgnoreCase(logFormat)) {
            throw new IllegalArgumentException(
                    "prabhix.observability.log-format must be 'console' or 'json', but was: " + logFormat);
        }
    }

    public boolean jsonLogging() {
        return "json".equalsIgnoreCase(logFormat);
    }
}
