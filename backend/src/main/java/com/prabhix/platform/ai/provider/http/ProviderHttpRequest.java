package com.prabhix.platform.ai.provider.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Map;

public record ProviderHttpRequest(
        HttpMethod method,
        String url,
        Map<String, String> headers,
        String body) {

    public ProviderHttpRequest withHeader(String name, String value) {
        var merged = new java.util.HashMap<>(headers == null ? Map.of() : headers);
        merged.put(name, value);
        return new ProviderHttpRequest(method, url, merged, body);
    }

    public static ProviderHttpRequest post(String url, String body, String apiKeyHeader, String apiKey) {
        return new ProviderHttpRequest(
                HttpMethod.POST,
                url,
                Map.of(
                        HttpHeaders.CONTENT_TYPE, "application/json",
                        apiKeyHeader, apiKey),
                body);
    }
}
