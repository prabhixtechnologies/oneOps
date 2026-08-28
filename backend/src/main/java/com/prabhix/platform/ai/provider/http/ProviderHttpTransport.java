package com.prabhix.platform.ai.provider.http;

public interface ProviderHttpTransport {

    ProviderHttpResponse execute(ProviderHttpRequest request);
}
