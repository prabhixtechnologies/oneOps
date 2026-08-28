package com.prabhix.platform.push.provider.apns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.push.config.PushProperties;
import com.prabhix.platform.push.domain.PushEnums;
import com.prabhix.platform.push.provider.PushProvider;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
public class ApnsPushProvider implements PushProvider {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private final PushProperties.Apns config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final PrivateKey privateKey;

    private volatile CachedJwt cachedJwt;

    public ApnsPushProvider(PushProperties.Apns config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        try {
            this.privateKey = parsePrivateKey(config.privateKey());
        } catch (Exception ex) {
            throw new IllegalStateException("APNs private key is invalid", ex);
        }
    }

    @Override
    public String providerId() {
        return "APNS";
    }

    @Override
    public boolean configured() {
        return config.configured();
    }

    @Override
    public SendResult send(SendRequest request) {
        if (request.platform() != PushEnums.Platform.APNS) {
            return SendResult.failed("APNs provider cannot send to " + request.platform());
        }
        try {
            String jwt = providerJwt();
            Map<String, Object> payload = buildPayload(request);
            URI uri = URI.create(config.host() + "/3/device/" + request.deviceToken());

            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(HTTP_TIMEOUT)
                    .header("authorization", "bearer " + jwt)
                    .header("apns-topic", config.bundleId())
                    .header("apns-push-type", "alert")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return SendResult.ok(response.headers().firstValue("apns-id").orElse("apns-sent"));
            }

            JsonNode error = objectMapper.readTree(response.body().isBlank() ? "{}" : response.body());
            String reason = error.path("reason").asText("APNs send failed");
            if (isInvalidToken(response.statusCode(), reason)) {
                return SendResult.invalidToken(request.deviceToken());
            }
            return SendResult.failed(reason);
        } catch (Exception ex) {
            log.warn("APNs send failed: {}", ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }

    private Map<String, Object> buildPayload(SendRequest request) {
        Map<String, Object> aps = new HashMap<>();
        String title = stringField(request.payload(), "title", request.notificationType());
        String body = stringField(request.payload(), "body", "");
        aps.put("alert", Map.of("title", title, "body", body));
        aps.put("sound", "default");

        Map<String, Object> payload = new HashMap<>();
        payload.put("aps", aps);
        if (request.payload() != null) {
            request.payload().forEach((key, value) -> {
                if (!"title".equals(key) && !"body".equals(key)) {
                    payload.put(key, value);
                }
            });
        }
        payload.putIfAbsent("type", request.notificationType());
        return payload;
    }

    private String stringField(Map<String, Object> payload, String key, String fallback) {
        if (payload == null || payload.get(key) == null) {
            return fallback == null ? "" : fallback;
        }
        return String.valueOf(payload.get(key));
    }

    private boolean isInvalidToken(int statusCode, String reason) {
        return statusCode == 410
                || "BadDeviceToken".equals(reason)
                || "Unregistered".equals(reason)
                || "ExpiredToken".equals(reason);
    }

    private String providerJwt() throws Exception {
        CachedJwt token = cachedJwt;
        if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return token.value();
        }
        synchronized (this) {
            token = cachedJwt;
            if (token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
                return token.value();
            }
            Instant now = Instant.now();
            String jwt = Jwts.builder()
                    .header().add("alg", "ES256").add("kid", config.keyId()).and()
                    .issuer(config.teamId())
                    .issuedAt(java.util.Date.from(now))
                    .expiration(java.util.Date.from(now.plusSeconds(3600)))
                    .subject(config.bundleId())
                    .signWith(privateKey, Jwts.SIG.ES256)
                    .compact();
            cachedJwt = new CachedJwt(jwt, now.plusSeconds(3300));
            return jwt;
        }
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private record CachedJwt(String value, Instant expiresAt) {
    }
}
