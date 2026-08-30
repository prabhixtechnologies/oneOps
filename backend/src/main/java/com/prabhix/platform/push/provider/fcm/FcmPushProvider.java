package com.prabhix.platform.push.provider.fcm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.push.config.PushProperties;
import com.prabhix.platform.push.domain.PushEnums;
import com.prabhix.platform.push.provider.PushProvider;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class FcmPushProvider implements PushProvider {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final PushProperties.Fcm config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JsonNode serviceAccount;

    private volatile CachedToken cachedToken;

    public FcmPushProvider(PushProperties.Fcm config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        try {
            this.serviceAccount = objectMapper.readTree(config.serviceAccountJson());
        } catch (Exception ex) {
            throw new IllegalStateException("FCM service account JSON is invalid", ex);
        }
    }

    @Override
    public String providerId() {
        return "FCM";
    }

    @Override
    public boolean configured() {
        return config.configured();
    }

    @Override
    public SendResult send(SendRequest request) {
        if (request.platform() != PushEnums.Platform.FCM) {
            return SendResult.failed("FCM provider cannot send to " + request.platform());
        }
        try {
            String accessToken = accessToken();
            Map<String, Object> body = buildMessage(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://fcm.googleapis.com/v1/projects/"
                            + config.projectId() + "/messages:send"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                return SendResult.ok(json.path("name").asText("fcm-sent"));
            }

            JsonNode error = objectMapper.readTree(response.body()).path("error");
            String message = error.path("message").asText("FCM send failed");
            Set<String> invalid = invalidTokens(error, request.deviceToken());
            if (!invalid.isEmpty()) {
                return new SendResult(false, null, message, invalid);
            }
            return SendResult.failed(message);
        } catch (Exception ex) {
            log.warn("FCM send failed: {}", ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }

    private Map<String, Object> buildMessage(SendRequest request) {
        Map<String, Object> message = new HashMap<>();
        message.put("token", request.deviceToken());

        Map<String, Object> data = new HashMap<>();
        if (request.payload() != null) {
            request.payload().forEach((key, value) -> data.put(key, String.valueOf(value)));
        }
        data.putIfAbsent("type", request.notificationType());
        message.put("data", data);

        String title = stringField(request.payload(), "title", request.notificationType());
        String body = stringField(request.payload(), "body", "");
        if (!title.isBlank() || !body.isBlank()) {
            message.put("notification", Map.of("title", title, "body", body));
        }
        return Map.of("message", message);
    }

    private String stringField(Map<String, Object> payload, String key, String fallback) {
        if (payload == null || payload.get(key) == null) {
            return fallback == null ? "" : fallback;
        }
        return String.valueOf(payload.get(key));
    }

    private Set<String> invalidTokens(JsonNode error, String deviceToken) {
        String status = error.path("status").asText("");
        String details = error.toString();
        if ("NOT_FOUND".equals(status) || details.contains("UNREGISTERED")) {
            return Set.of(deviceToken);
        }
        return Set.of();
    }

    private String accessToken() throws Exception {
        CachedToken token = cachedToken;
        if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return token.value();
        }
        synchronized (this) {
            token = cachedToken;
            if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
                return token.value();
            }
            cachedToken = fetchAccessToken();
            return cachedToken.value();
        }
    }

    private CachedToken fetchAccessToken() throws Exception {
        String jwt = buildServiceAccountJwt();
        String form = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt;
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google OAuth token exchange failed: HTTP "
                    + response.statusCode());
        }
        JsonNode json = objectMapper.readTree(response.body());
        long expiresIn = json.path("expires_in").asLong(3600);
        return new CachedToken(json.path("access_token").asText(),
                Instant.now().plusSeconds(Math.max(60, expiresIn - 30)));
    }

    private String buildServiceAccountJwt() throws Exception {
        Instant now = Instant.now();
        PrivateKey privateKey = parsePrivateKey(serviceAccount.path("private_key").asText());
        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .issuer(serviceAccount.path("client_email").asText())
                .audience().add("https://oauth2.googleapis.com/token").and()
                .claim("scope", FCM_SCOPE)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(3600)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
