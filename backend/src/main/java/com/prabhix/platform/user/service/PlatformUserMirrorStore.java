package com.prabhix.platform.user.service;

import com.prabhix.identity.client.IdentityUser;
import com.prabhix.identity.client.UserMirrorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Where this platform keeps its copy of an identity user.
 *
 * <p>Twenty-one tables here have a foreign key to {@code users.id} — {@code created_by},
 * {@code assignee_id}, {@code organization_memberships.user_id}. Those cannot point across a service
 * boundary, so this database keeps a thin mirror keyed by the identity {@code sub}. The starter's
 * {@code IdentityUserMirror} decides when to write and what identity said; this is the write.
 *
 * <p>Written with SQL rather than through the repository on purpose. The row has to keep identity's
 * id, and Hibernate's {@code @UuidGenerator} replaces an assigned identifier with a fresh one, so a
 * {@code save()} would silently produce a mirror under the wrong primary key — which is the one thing
 * this must not do. {@code ON CONFLICT} also makes two simultaneous first requests harmless.
 */
@Component
@RequiredArgsConstructor
public class PlatformUserMirrorStore implements UserMirrorStore {

    private final JdbcTemplate jdbc;

    @Override
    public void upsert(IdentityUser user) {
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
                user.emailVerified() ? Timestamp.from(now) : null,
                blankToPlaceholder(user.fullName(), user.email()),
                user.displayName(),
                user.avatarUrl(),
                user.jobTitle(),
                defaulted(user.timezone(), "Asia/Kolkata"),
                defaulted(user.locale(), "en-IN"),
                defaulted(user.status(), "ACTIVE"),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    /** {@code full_name} is NOT NULL here and optional in identity, so the address stands in for it. */
    private static String blankToPlaceholder(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
