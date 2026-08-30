package com.prabhix.platform.mail.outbound;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies AWS SNS message signatures so bounce/complaint webhooks cannot be forged.
 *
 * <p>Certificates are fetched from {@code SigningCertURL} and cached by URL. Only HTTPS URLs
 * on {@code sns.*.amazonaws.com} are accepted, matching AWS guidance.
 */
@Slf4j
@Component
public class SnsMessageVerifier {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, PublicKey> certificateCache = new ConcurrentHashMap<>();

    public SnsMessageVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(FETCH_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean verify(JsonNode message, byte[] rawBody) {
        String type = message.path("Type").asText("");
        String signature = message.path("Signature").asText(null);
        String signingCertUrl = message.path("SigningCertURL").asText(null);
        String signatureVersion = message.path("SignatureVersion").asText("1");

        if (signature == null || signingCertUrl == null || type.isBlank()) {
            return false;
        }
        if (!isAllowedCertUrl(signingCertUrl)) {
            log.warn("Rejected SNS SigningCertURL outside amazonaws.com: {}", signingCertUrl);
            return false;
        }

        try {
            PublicKey publicKey = certificateCache.computeIfAbsent(signingCertUrl, this::fetchPublicKey);
            String stringToSign = buildStringToSign(message, type);
            Signature verifier = "2".equals(signatureVersion)
                    ? Signature.getInstance("SHA256withRSA")
                    : Signature.getInstance("SHA1withRSA");
            verifier.initVerify(publicKey);
            verifier.update(stringToSign.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception ex) {
            log.warn("SNS signature verification failed: {}", ex.getMessage());
            return false;
        }
    }

    String buildStringToSign(JsonNode message, String type) {
        return switch (type) {
            case "Notification" -> canonicalize(message,
                    "Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type");
            case "SubscriptionConfirmation", "UnsubscribeConfirmation" -> canonicalize(message,
                    "Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type");
            default -> canonicalize(message, "Message", "MessageId", "Timestamp", "TopicArn", "Type");
        };
    }

    private String canonicalize(JsonNode message, String... keys) {
        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            if (!message.has(key)) {
                continue;
            }
            JsonNode value = message.get(key);
            if (value.isNull()) {
                continue;
            }
            builder.append(key).append('\n').append(value.asText()).append('\n');
        }
        return builder.toString();
    }

    private boolean isAllowedCertUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            return host != null && host.endsWith(".amazonaws.com") && host.startsWith("sns.");
        } catch (Exception ex) {
            return false;
        }
    }

    private PublicKey fetchPublicKey(String certUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(certUrl))
                    .GET()
                    .timeout(FETCH_TIMEOUT)
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(response.body()));
            return certificate.getPublicKey();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not fetch SNS signing certificate", ex);
        }
    }
}
