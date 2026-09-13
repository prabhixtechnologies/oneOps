package com.prabhix.platform.ops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Product healthz URLs probed by the ops hub. An empty map uses the built-in production defaults.
 */
@Validated
@ConfigurationProperties(prefix = "prabhix.ops.health")
public record OpsHealthProperties(
        @DefaultValue Map<String, String> products,
        @DefaultValue("5") int timeoutSeconds) {

    public static Map<String, String> defaultProducts() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("identity", "https://api.prabhixtechnologies.com/healthz");
        defaults.put("oneops", "https://oneops.prabhixtechnologies.com/healthz");
        defaults.put("mobistack", "https://mobistack.prabhixtechnologies.com/healthz");
        defaults.put("mailroom", "https://mailroom.prabhixtechnologies.com/healthz");
        defaults.put("store", "https://store.prabhixtechnologies.com/healthz");
        return defaults;
    }

    public Map<String, String> resolvedProducts() {
        if (products == null || products.isEmpty()) {
            return defaultProducts();
        }
        return products;
    }
}
