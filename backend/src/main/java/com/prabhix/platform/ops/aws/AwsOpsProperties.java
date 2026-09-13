package com.prabhix.platform.ops.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Optional AWS read surface for the platform ops hub.
 *
 * <p>Credentials come from {@code DefaultCredentialsProvider} (instance role / env / shared
 * config) — never from static keys in this properties block.
 */
@Validated
@ConfigurationProperties(prefix = "prabhix.ops.aws")
public record AwsOpsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("ap-south-1") String region,
        /** Cost Explorer is account-global and must be called via the us-east-1 endpoint. */
        @DefaultValue("us-east-1") String ceRegion,
        @DefaultValue("1800") int costCacheTtlSeconds) {
}
