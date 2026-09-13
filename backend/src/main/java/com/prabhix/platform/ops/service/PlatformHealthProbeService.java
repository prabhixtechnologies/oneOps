package com.prabhix.platform.ops.service;

import com.prabhix.platform.ops.config.OpsHealthProperties;
import com.prabhix.platform.ops.dto.OpsDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP probes against product {@code /healthz} URLs for the admin ops hub.
 */
@Slf4j
@Service
public class PlatformHealthProbeService {

    private final OpsHealthProperties properties;
    private final HttpClient httpClient;

    public PlatformHealthProbeService(OpsHealthProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public OpsDtos.ProductHealthResponse probeProducts() {
        Map<String, String> urls = properties.resolvedProducts();
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.timeoutSeconds()));
        List<OpsDtos.ProductHealthRow> products = new ArrayList<>();

        for (Map.Entry<String, String> entry : urls.entrySet()) {
            String name = entry.getKey();
            String url = entry.getValue();
            Instant started = Instant.now();
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .GET()
                        .header("Accept", "*/*")
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                long latencyMs = Duration.between(started, Instant.now()).toMillis();
                int status = response.statusCode();
                boolean ok = status >= 200 && status < 400;
                products.add(new OpsDtos.ProductHealthRow(
                        name,
                        url,
                        ok,
                        status,
                        latencyMs,
                        ok ? null : "HTTP " + status));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                products.add(new OpsDtos.ProductHealthRow(name, url, false, null, null, "interrupted"));
            } catch (Exception ex) {
                String error = ex.getMessage();
                if (error == null || error.isBlank()) {
                    error = ex.getClass().getSimpleName();
                }
                if (error.toLowerCase().contains("timed out") || error.toLowerCase().contains("timeout")) {
                    error = "timeout";
                }
                log.debug("Health probe {} failed: {}", name, error);
                products.add(new OpsDtos.ProductHealthRow(name, url, false, null, null, error));
            }
        }

        int healthy = (int) products.stream().filter(OpsDtos.ProductHealthRow::ok).count();
        return new OpsDtos.ProductHealthResponse(
                healthy == products.size() && !products.isEmpty(),
                Instant.now(),
                healthy,
                products.size(),
                products);
    }
}
