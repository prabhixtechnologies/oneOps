package com.prabhix.platform.observability.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.util.Optional;

/**
 * Publishes Micrometer meters to CloudWatch when explicitly enabled.
 *
 * <p>Off by default so local tests and laptops never call AWS. Turn on with
 * {@code CLOUDWATCH_METRICS=true} or the {@code cloudwatch} Spring profile. Namespace is
 * {@code Prabhix} for Identity, oneOps and MobiStack so one dashboard can filter by the
 * {@code application} common tag.
 *
 * <p>Spring Boot 4 kept Prometheus and OTLP export auto-configuration and dropped CloudWatch,
 * so this bean is the equivalent of adding {@code micrometer-registry-cloudwatch2} under
 * {@code management.cloudwatch.metrics.export}.
 */
@Configuration
@ConditionalOnClass(CloudWatchMeterRegistry.class)
@ConditionalOnProperty(prefix = "management.cloudwatch.metrics.export", name = "enabled", havingValue = "true")
public class CloudWatchMetricsConfiguration {

    static final String NAMESPACE = "Prabhix";

    @Bean(destroyMethod = "close")
    CloudWatchAsyncClient cloudWatchAsyncClient() {
        String region = Optional.ofNullable(System.getenv("AWS_REGION"))
                .or(() -> Optional.ofNullable(System.getenv("AWS_DEFAULT_REGION")))
                .orElse("ap-south-1");
        return CloudWatchAsyncClient.builder().region(Region.of(region)).build();
    }

    @Bean
    CloudWatchMeterRegistry cloudWatchMeterRegistry(Clock clock, CloudWatchAsyncClient client) {
        CloudWatchConfig config = key -> switch (key) {
            case "cloudwatch.namespace" -> NAMESPACE;
            case "cloudwatch.enabled" -> "true";
            case "cloudwatch.step" -> "PT1M";
            default -> null;
        };
        return new CloudWatchMeterRegistry(config, clock, client);
    }
}
