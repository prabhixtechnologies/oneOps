package com.prabhix.platform.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "prabhix.push")
public record PushProperties(
        @DefaultValue("LOGGING") String provider,
        @DefaultValue Outbox outbox,
        @DefaultValue Fcm fcm,
        @DefaultValue Apns apns) {

    public record Outbox(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("50") int batchSize,
            @DefaultValue("PT5S") Duration pollInterval,
            @DefaultValue("6") int maxAttempts) {
    }

    public record Fcm(
            @DefaultValue("") String projectId,
            @DefaultValue("") String serviceAccountJson) {

        public boolean configured() {
            return projectId != null && !projectId.isBlank()
                    && serviceAccountJson != null && !serviceAccountJson.isBlank();
        }
    }

    public record Apns(
            @DefaultValue("") String teamId,
            @DefaultValue("") String keyId,
            @DefaultValue("") String bundleId,
            @DefaultValue("") String privateKey,
            @DefaultValue("https://api.push.apple.com") String host) {

        public boolean configured() {
            return teamId != null && !teamId.isBlank()
                    && keyId != null && !keyId.isBlank()
                    && bundleId != null && !bundleId.isBlank()
                    && privateKey != null && !privateKey.isBlank();
        }
    }
}
