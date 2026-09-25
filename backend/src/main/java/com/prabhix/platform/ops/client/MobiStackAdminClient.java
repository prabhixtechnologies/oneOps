package com.prabhix.platform.ops.client;

import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.identity.client.IdentityInternalClient;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.ops.config.MobiStackAdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * MobiStack's platform admin API, over the shared service token.
 *
 * <p>Every call names the staff member it is being made for. MobiStack records that person, not
 * "the platform", and a shop JWT is not accepted on the other side.
 */
public class MobiStackAdminClient {

    private static final Logger log = LoggerFactory.getLogger(MobiStackAdminClient.class);

    private final MobiStackAdminProperties mobistack;
    private final IdentityClientProperties identity;
    private final RestClient http;

    public MobiStackAdminClient(MobiStackAdminProperties mobistack,
                                IdentityClientProperties identity,
                                RestClient.Builder builders) {
        this.mobistack = mobistack;
        this.identity = identity;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(mobistack.timeout());
        factory.setReadTimeout(mobistack.timeout());
        this.http = builders.clone().requestFactory(factory).build();
    }

    public boolean enabled() {
        return mobistack.enabled() && identity.canCallInternal();
    }

    public Object get(UUID actor, String path) {
        return get(actor, path, Map.of());
    }

    public Object get(UUID actor, String path, Map<String, ?> query) {
        String target = UriComponentsBuilder.fromUriString(url(path))
                .queryParams(toParams(query))
                .build(true)
                .toUriString();
        return call("GET " + path, spec -> spec.get()
                .uri(target)
                .headers(h -> headers(h, actor, null))
                .retrieve()
                .body(Object.class));
    }

    public Object post(UUID actor, String path, Object body, String reason) {
        return call("POST " + path, spec -> spec.post()
                .uri(url(path))
                .headers(h -> headers(h, actor, reason))
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(Object.class));
    }

    public Object put(UUID actor, String path, Object body, String reason) {
        return call("PUT " + path, spec -> spec.put()
                .uri(url(path))
                .headers(h -> headers(h, actor, reason))
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(Object.class));
    }

    private String url(String path) {
        return mobistack.internalBaseUrl() + "/api/v1/mobistack/admin" + path;
    }

    private void headers(org.springframework.http.HttpHeaders headers, UUID actor, String reason) {
        headers.set(IdentityInternalClient.SERVICE_TOKEN_HEADER, identity.serviceToken());
        if (actor == null) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "An admin call must name the acting user");
        }
        headers.set(IdentityInternalClient.ACTING_USER_HEADER, actor.toString());
        if (reason != null && !reason.isBlank()) {
            headers.set(IdentityInternalClient.ACTING_REASON_HEADER, reason.trim());
        }
    }

    private static org.springframework.util.MultiValueMap<String, String> toParams(Map<String, ?> query) {
        org.springframework.util.LinkedMultiValueMap<String, String> params =
                new org.springframework.util.LinkedMultiValueMap<>();
        if (query == null) {
            return params;
        }
        query.forEach((key, value) -> {
            if (value != null && !String.valueOf(value).isBlank()) {
                params.add(key, String.valueOf(value));
            }
        });
        return params;
    }

    private Object call(String what, Function<RestClient, Object> request) {
        if (!enabled()) {
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "MobiStack admin is not configured (MOBISTACK_INTERNAL_URL / IDENTITY_SERVICE_TOKEN)");
        }
        try {
            return request.apply(http);
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            log.warn("MobiStack refused {}: {} {}", what, status.value(), ex.getResponseBodyAsString());
            if (status.value() == 404) {
                throw ApiException.of(ErrorCode.NOT_FOUND, "MobiStack does not know that resource");
            }
            if (status.value() == 401 || status.value() == 403) {
                throw ApiException.of(ErrorCode.FORBIDDEN, "MobiStack refused the request");
            }
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "MobiStack refused the request (" + status.value() + ")");
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            log.error("MobiStack unavailable for {}: {}", what, ex.getMessage());
            throw ApiException.of(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "MobiStack could not be reached. Try again.");
        }
    }
}
