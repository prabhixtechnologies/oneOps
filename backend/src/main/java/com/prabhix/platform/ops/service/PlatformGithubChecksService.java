package com.prabhix.platform.ops.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.ops.config.OpsGithubProperties;
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

/**
 * Latest GitHub Actions run per configured repo. Requires {@code GITHUB_TOKEN}; without it
 * returns an empty list and a note so the hub still loads.
 */
@Slf4j
@Service
public class PlatformGithubChecksService {

    private final OpsGithubProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public PlatformGithubChecksService(OpsGithubProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public OpsDtos.GithubChecksResponse latestChecks() {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            return new OpsDtos.GithubChecksResponse(
                    true,
                    "GITHUB_TOKEN not set; returning empty checks",
                    Instant.now(),
                    List.of());
        }

        String apiBase = properties.apiUrl() == null || properties.apiUrl().isBlank()
                ? "https://api.github.com"
                : properties.apiUrl().replaceAll("/+$", "");
        List<OpsDtos.GithubCheckRow> checks = new ArrayList<>();
        boolean allOk = true;

        for (String fullName : properties.repos()) {
            if (fullName == null || fullName.isBlank()) {
                continue;
            }
            try {
                String url = apiBase + "/repos/" + fullName.trim()
                        + "/actions/runs?per_page=1&exclude_pull_requests=true";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .header("Accept", "application/vnd.github+json")
                        .header("Authorization", "Bearer " + token.trim())
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "prabhix-platform-ops")
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    checks.add(new OpsDtos.GithubCheckRow(fullName, null, null, null, null));
                    allOk = false;
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("GitHub checks for {} returned HTTP {}", fullName, response.statusCode());
                    checks.add(new OpsDtos.GithubCheckRow(fullName, null, null, null, null));
                    allOk = false;
                    continue;
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode runs = root.path("workflow_runs");
                if (!runs.isArray() || runs.isEmpty()) {
                    checks.add(new OpsDtos.GithubCheckRow(fullName, null, "none", "none", null));
                    continue;
                }
                JsonNode run = runs.get(0);
                String conclusion = textOrNull(run.get("conclusion"));
                String status = textOrNull(run.get("status"));
                String name = textOrNull(run.get("name"));
                String htmlUrl = textOrNull(run.get("html_url"));
                boolean runOk = "success".equals(conclusion)
                        || (status != null && !"completed".equals(status));
                if ("completed".equals(status) && conclusion != null
                        && !"success".equals(conclusion) && !"none".equals(conclusion)) {
                    allOk = false;
                }
                if (!runOk && "completed".equals(status)) {
                    allOk = false;
                }
                checks.add(new OpsDtos.GithubCheckRow(fullName, name, status, conclusion, htmlUrl));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                checks.add(new OpsDtos.GithubCheckRow(fullName, null, null, null, null));
                allOk = false;
            } catch (Exception ex) {
                log.warn("GitHub check for {} failed: {}", fullName, ex.getMessage());
                checks.add(new OpsDtos.GithubCheckRow(fullName, null, null, null, null));
                allOk = false;
            }
        }

        return new OpsDtos.GithubChecksResponse(allOk, null, Instant.now(), checks);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return node != null && !node.isNull() ? node.asText() : null;
        }
        String value = node.asText();
        return value == null || value.isBlank() || "null".equals(value) ? null : value;
    }
}
