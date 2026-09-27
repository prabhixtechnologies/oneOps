package com.prabhix.platform.auth.web;

import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Preserves legacy {@code /api/v1/oneops/auth/*} verification and reset links by forwarding them to Identity.
 *
 * <p>Email templates and bookmarks still target OneOps host paths; the edge may route here rather than
 * Identity directly. Identity remains the system of record for credential flows.
 */
@RestController
@RequestMapping("/api/v1/oneops/auth")
@RequiredArgsConstructor
public class IdentityAuthBridgeController {

    private final IdentityClientProperties identity;
    private final RestClient.Builder restClientBuilder;

    @PostMapping("/email/verify/confirm")
    public Map<String, Object> confirmEmail(@RequestBody Map<String, String> body) {
        return forward("/api/v1/identity/auth/email/verify/confirm", body, null);
    }

    @PostMapping("/password/reset")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> body) {
        return forward("/api/v1/identity/auth/password/reset", body, null);
    }

    @PostMapping("/password/forgot")
    public Map<String, Object> forgotPassword(@RequestBody Map<String, String> body) {
        return forward("/api/v1/identity/auth/password/forgot", body, null);
    }

    private Map<String, Object> forward(String path, Map<String, String> body, String bearer) {
        if (identity.internalBaseUrl() == null || identity.internalBaseUrl().isBlank()) {
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE, "Identity is not configured");
        }
        String base = identity.internalBaseUrl().replaceAll("/+$", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null && !bearer.isBlank()) {
            headers.setBearerAuth(bearer);
        }
        RestClient client = restClientBuilder.baseUrl(base).build();
        ResponseEntity<Map> response = client.method(HttpMethod.POST)
                .uri(path)
                .headers(h -> h.addAll(headers))
                .body(body)
                .retrieve()
                .toEntity(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = response.getBody() == null ? Map.of() : response.getBody();
        return payload;
    }
}
