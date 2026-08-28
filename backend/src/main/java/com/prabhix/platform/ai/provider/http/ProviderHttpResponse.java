package com.prabhix.platform.ai.provider.http;

public record ProviderHttpResponse(int statusCode, String body) {

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isRetryable() {
        return statusCode == 429 || statusCode >= 500;
    }

    public boolean isAuthFailure() {
        return statusCode == 401 || statusCode == 403;
    }
}
