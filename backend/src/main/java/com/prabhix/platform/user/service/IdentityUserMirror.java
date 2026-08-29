package com.prabhix.platform.user.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fills in the local {@code users} row for someone identity knows about and this database does not.
 *
 * <p>Twenty-one tables here have a foreign key to {@code users.id} — {@code created_by},
 * {@code assignee_id}, {@code organization_memberships.user_id}. Those cannot point across a service
 * boundary, so each product keeps a thin mirror keyed by the identity {@code sub}. The bulk import
 * seeds it; this covers everyone who signs up afterwards, on the first request that names them.
 *
 * <p>Written with SQL rather than through the repository on purpose. The row has to keep identity's
 * id, and Hibernate's {@code @UuidGenerator} replaces an assigned identifier with a fresh one, so a
 * {@code save()} would silently produce a mirror under the wrong primary key — which is the one thing
 * this must not do. {@code ON CONFLICT} also makes two simultaneous first requests harmless.
 */
@Slf4j
@Service
public class IdentityUserMirror {

    private final PrabhixProperties.Security.Identity config;
    private final RestClient http;
    private final JdbcTemplate jdbc;

    public IdentityUserMirror(PrabhixProperties properties,
                              RestClient.Builder httpBuilder,
                              JdbcTemplate jdbc) {
        this.config = properties.security().identity();
        this.jdbc = jdbc;
        this.http = httpBuilder.build();
    }

    /**
     * Creates or refreshes the mirror row for one identity subject.
     *
     * @throws ApiException if identity does not know the subject either, or cannot be asked
     */
    public void pull(UUID subject) {
        if (!config.canMirror()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED,
                    "This account is not provisioned on the platform");
        }

        LookupResponse response;
        try {
            response = http.post()
                    .uri(config.internalBaseUrl() + "/internal/users/lookup")
                    .header("X-Prabhix-Service-Token", config.serviceToken())
                    .body(new LookupRequest(List.of(subject), List.of()))
                    .retrieve()
                    .body(LookupResponse.class);
        } catch (RuntimeException ex) {
            log.error("Could not reach identity to mirror user {}: {}", subject, ex.getMessage());
            throw ApiException.of(ErrorCode.INTERNAL_ERROR,
                    "Could not verify your account right now. Try again.");
        }

        MirroredUser user = response == null || response.users() == null || response.users().isEmpty()
                ? null
                : response.users().get(0);
        if (user == null) {
            // Identity signed a token for a subject it will not describe: deleted between issuing the
            // token and this request, most likely. Either way there is nobody to authorize.
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "That account no longer exists");
        }

        upsert(user);
        log.info("Mirrored identity user {} into the platform", subject);
    }

    private void upsert(MirroredUser user) {
        Instant now = Instant.now();
        // platform_admin is deliberately absent from both the insert and the update. Platform staff
        // authority is granted in this database and nowhere else: mirroring identity's copy would mean
        // a compromised identity service could elevate itself here, which is exactly the blast radius
        // the split was meant to remove. Identity's own column is a leftover of the bulk import.
        jdbc.update("""
                INSERT INTO users (id, email, email_verified_at, full_name, display_name, avatar_url,
                                   job_title, timezone, locale, status, platform_admin,
                                   version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, 0, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    email_verified_at = EXCLUDED.email_verified_at,
                    full_name = EXCLUDED.full_name,
                    display_name = EXCLUDED.display_name,
                    avatar_url = EXCLUDED.avatar_url,
                    job_title = EXCLUDED.job_title,
                    timezone = EXCLUDED.timezone,
                    locale = EXCLUDED.locale,
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at
                """,
                user.id(),
                user.email(),
                user.emailVerified() ? java.sql.Timestamp.from(now) : null,
                blankToPlaceholder(user.fullName(), user.email()),
                user.displayName(),
                user.avatarUrl(),
                user.jobTitle(),
                defaulted(user.timezone(), "Asia/Kolkata"),
                defaulted(user.locale(), "en-IN"),
                defaulted(user.status(), "ACTIVE"),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
    }

    /** {@code full_name} is NOT NULL here and optional in identity, so the address stands in for it. */
    private static String blankToPlaceholder(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record LookupRequest(List<UUID> ids, List<String> emails) {
    }

    private record LookupResponse(List<MirroredUser> users) {
    }

    private record MirroredUser(UUID id,
                                String email,
                                boolean emailVerified,
                                String fullName,
                                String displayName,
                                String avatarUrl,
                                String jobTitle,
                                String timezone,
                                String locale,
                                String status,
                                boolean platformAdmin,
                                Instant updatedAt) {
    }
}
