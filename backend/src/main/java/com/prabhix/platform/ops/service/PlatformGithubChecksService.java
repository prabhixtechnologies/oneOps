package com.prabhix.platform.ops.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
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
import java.util.Map;

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

    public OpsDtos.GithubPullsResponse openPulls() {
        String token = githubToken();
        if (token == null) {
            return new OpsDtos.GithubPullsResponse(false,
                    "GITHUB_TOKEN not set; returning empty pull requests", Instant.now(), List.of());
        }
        List<OpsDtos.GithubPullRow> pulls = new ArrayList<>();
        boolean ok = true;
        for (String fullName : properties.repos()) {
            if (fullName == null || fullName.isBlank()) {
                continue;
            }
            try {
                JsonNode root = githubGet(token, "/repos/" + fullName.trim() + "/pulls?state=open&per_page=5");
                if (root == null || !root.isArray()) {
                    ok = false;
                    continue;
                }
                for (JsonNode pull : root) {
                    pulls.add(new OpsDtos.GithubPullRow(
                            fullName.trim(),
                            pull.path("number").isIntegralNumber() ? pull.path("number").asInt() : null,
                            textOrNull(pull.get("title")),
                            textOrNull(pull.path("user").path("login")),
                            textOrNull(pull.get("html_url")),
                            textOrNull(pull.get("state"))));
                }
            } catch (Exception ex) {
                log.warn("GitHub pulls for {} failed: {}", fullName, ex.getMessage());
                ok = false;
            }
        }
        return new OpsDtos.GithubPullsResponse(ok, null, Instant.now(), pulls);
    }

    public OpsDtos.GithubDeploysResponse recentDeploys() {
        String token = githubToken();
        if (token == null) {
            return new OpsDtos.GithubDeploysResponse(false,
                    "GITHUB_TOKEN not set; returning empty deploy runs", Instant.now(), List.of());
        }
        try {
            JsonNode root = githubGet(token, "/repos/" + properties.deployRepo()
                    + "/actions/workflows/" + properties.deployWorkflow()
                    + "/runs?per_page=10");
            if (root == null) {
                return new OpsDtos.GithubDeploysResponse(false, "GitHub returned no deploy runs",
                        Instant.now(), List.of());
            }
            List<OpsDtos.GithubDeployRow> runs = new ArrayList<>();
            for (JsonNode run : root.path("workflow_runs")) {
                Instant created = null;
                String createdRaw = textOrNull(run.get("created_at"));
                if (createdRaw != null) {
                    try {
                        created = Instant.parse(createdRaw);
                    } catch (Exception ignored) {
                        created = null;
                    }
                }
                runs.add(new OpsDtos.GithubDeployRow(
                        run.path("id").isIntegralNumber() ? run.path("id").asLong() : null,
                        textOrNull(run.get("name")),
                        textOrNull(run.get("status")),
                        textOrNull(run.get("conclusion")),
                        textOrNull(run.get("html_url")),
                        textOrNull(run.get("head_sha")),
                        created));
            }
            return new OpsDtos.GithubDeploysResponse(true, null, Instant.now(), runs);
        } catch (Exception ex) {
            log.warn("GitHub deploy runs failed: {}", ex.getMessage());
            return new OpsDtos.GithubDeploysResponse(false, ex.getMessage(), Instant.now(), List.of());
        }
    }

    /**
     * Dispatches {@code Infra/.github/workflows/deploy.yml}. Returns 503 when no token is configured
     * rather than a silent no-op — Promote is a write, and a button that does nothing is worse than
     * an error.
     */
    public OpsDtos.PromoteResponse promote(String service, String tag) {
        String token = githubToken();
        if (token == null) {
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "GITHUB_TOKEN is not configured; Promote cannot dispatch the deploy workflow.");
        }
        if (tag == null || tag.isBlank()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "tag is required");
        }
        if (service == null || service.isBlank()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "service is required");
        }
        String input = tagInputFor(service);
        try {
            String url = apiBase() + "/repos/" + properties.deployRepo()
                    + "/actions/workflows/" + properties.deployWorkflow() + "/dispatches";
            String payload = objectMapper.writeValueAsString(Map.of(
                    "ref", properties.deployRef(),
                    "inputs", Map.of(
                            "tag", tag.trim(),
                            input, tag.trim())));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "prabhix-platform-ops")
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || (response.statusCode() >= 200 && response.statusCode() < 300)) {
                String html = "https://github.com/" + properties.deployRepo() + "/actions/workflows/"
                        + properties.deployWorkflow();
                return new OpsDtos.PromoteResponse(true,
                        "Dispatched deploy.yml for " + service + " @ " + tag.trim(), html);
            }
            log.warn("GitHub workflow_dispatch returned HTTP {}: {}", response.statusCode(), response.body());
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "GitHub refused the dispatch (HTTP " + response.statusCode() + ")");
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE, "GitHub dispatch was interrupted");
        } catch (Exception ex) {
            log.error("GitHub dispatch failed: {}", ex.getMessage());
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE, "GitHub could not be reached. Try again.");
        }
    }

    private JsonNode githubGet(String token, String path) throws Exception {
        String url = apiBase() + path;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "prabhix-platform-ops")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("GitHub GET {} returned HTTP {}", path, response.statusCode());
            return null;
        }
        return objectMapper.readTree(response.body());
    }

    private static String githubToken() {
        String token = System.getenv("GITHUB_TOKEN");
        return token == null || token.isBlank() ? null : token.trim();
    }

    private String apiBase() {
        return properties.apiUrl() == null || properties.apiUrl().isBlank()
                ? "https://api.github.com"
                : properties.apiUrl().replaceAll("/+$", "");
    }

    static String tagInputFor(String service) {
        String key = service.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return switch (key) {
            case "backend", "oneops", "oneops-backend" -> "backend_tag";
            case "web", "oneops-web" -> "web_tag";
            case "admin" -> "admin_tag";
            case "marketing" -> "marketing_tag";
            case "identity" -> "identity_tag";
            case "mailroom" -> "mailroom_tag";
            case "mobistack", "mobistack-backend" -> "mobistack_backend_tag";
            case "mobistack-web" -> "mobistack_web_tag";
            case "app-store", "store" -> "app_store_tag";
            default -> "tag";
        };
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return node != null && !node.isNull() ? node.asText() : null;
        }
        String value = node.asText();
        return value == null || value.isBlank() || "null".equals(value) ? null : value;
    }
}
