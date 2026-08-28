package com.prabhix.platform.ai.provider.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class RestClientProviderHttpTransport implements ProviderHttpTransport {

    private final RestClient restClient = RestClient.create();

    @Override
    public ProviderHttpResponse execute(ProviderHttpRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient.method(request.method())
                    .uri(request.url())
                    .contentType(MediaType.APPLICATION_JSON);
            if (request.headers() != null) {
                for (Map.Entry<String, String> entry : request.headers().entrySet()) {
                    spec = spec.header(entry.getKey(), entry.getValue());
                }
            }
            String body;
            if (request.body() != null) {
                body = spec.body(request.body()).retrieve().body(String.class);
            } else {
                body = spec.retrieve().body(String.class);
            }
            return new ProviderHttpResponse(200, body == null ? "" : body);
        } catch (RestClientResponseException ex) {
            return new ProviderHttpResponse(ex.getStatusCode().value(),
                    ex.getResponseBodyAsString() == null ? "" : ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.debug("Provider HTTP transport error: {}", ex.getMessage());
            return new ProviderHttpResponse(503, ex.getMessage() == null ? "" : ex.getMessage());
        }
    }

    public ProviderHttpResponse executeWithTimeout(ProviderHttpRequest request, Duration timeout) {
        // RestClient uses default timeouts; request-timeout is enforced at orchestrator level
        return execute(request);
    }
}
