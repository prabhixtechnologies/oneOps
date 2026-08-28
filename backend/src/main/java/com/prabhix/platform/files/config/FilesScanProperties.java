package com.prabhix.platform.files.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "prabhix.files.scan")
public record FilesScanProperties(
        @DefaultValue("NOOP") String provider,
        @DefaultValue ClamAv clamav,
        /**
         * When ClamAV is selected, treat scanner failures as infected rather than clean.
         * Disabling this lets uploads proceed during an outage — safer for availability,
         * unsafe for security because malware would not be caught.
         */
        @DefaultValue("true") boolean failClosedOnError) {

    public record ClamAv(
            @DefaultValue("localhost") String host,
            @DefaultValue("3310") int port,
            @DefaultValue("60000") int connectTimeoutMillis,
            @DefaultValue("120000") int scanTimeoutMillis,
            @DefaultValue("2048") int chunkSize) {

        public boolean configured() {
            return host != null && !host.isBlank() && port > 0;
        }
    }
}
