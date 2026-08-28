package com.prabhix.platform.security.jwt;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Issues and verifies access tokens. */
@Slf4j
@Service
public class JwtService {

    static final String CLAIM_ORG = "org";
    static final String CLAIM_PERMISSIONS = "perms";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_SESSION = "sid";
    static final String CLAIM_PLATFORM_ADMIN = "padm";
    static final String CLAIM_TYPE = "typ";

    private static final String TYPE_ACCESS = "access";
    private static final int MIN_SECRET_LENGTH = 64;
    private static final String DEV_SECRET_MARKER = "dev-only-insecure";

    private final PrabhixProperties properties;
    private final Environment environment;
    private final SecretKey signingKey;

    public JwtService(PrabhixProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        this.signingKey = Keys.hmacShaKeyFor(
                properties.security().jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Refuses to start a non-dev instance with a weak or default signing key. A forgotten
     * {@code JWT_SECRET} in production would let anyone mint tokens, so this is a hard fail
     * rather than a warning.
     */
    @PostConstruct
    void validateSecretStrength() {
        String secret = properties.security().jwt().secret();
        boolean devProfile = List.of(environment.getActiveProfiles()).contains("dev")
                || environment.getActiveProfiles().length == 0;

        if (devProfile) {
            if (secret.contains(DEV_SECRET_MARKER)) {
                log.warn("Using the built-in development JWT secret. Never do this outside dev.");
            }
            return;
        }

        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters outside dev");
        }
        if (secret.contains(DEV_SECRET_MARKER)) {
            throw new IllegalStateException("JWT_SECRET is still the development default");
        }
    }

    public IssuedToken issue(PrabhixPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.security().jwt().accessTokenTtl());

        String token = Jwts.builder()
                .issuer(properties.security().jwt().issuer())
                .subject(principal.userId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_EMAIL, principal.email())
                .claim(CLAIM_NAME, principal.displayName())
                .claim(CLAIM_ORG, principal.organizationId() == null
                        ? null : principal.organizationId().toString())
                .claim(CLAIM_PERMISSIONS, principal.permissions().stream()
                        .map(Permission::name)
                        .sorted()
                        .toList())
                .claim(CLAIM_SESSION, principal.sessionId() == null
                        ? null : principal.sessionId().toString())
                .claim(CLAIM_PLATFORM_ADMIN, principal.platformAdmin() ? Boolean.TRUE : null)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, expiresAt,
                properties.security().jwt().accessTokenTtl().toSeconds());
    }

    /**
     * @throws ApiException with {@link ErrorCode#TOKEN_EXPIRED} or
     *         {@link ErrorCode#TOKEN_INVALID}; never returns null
     */
    public PrabhixPrincipal parse(String token) {
        return parseDetailed(token).principal();
    }

    /**
     * Like {@link #parse(String)} but also returns the issued-at claim, which
     * {@link TokenDenyList} needs to tell a token minted before a user-wide revocation from one
     * minted after it. Kept separate so the signature is verified once per request rather than
     * twice, which re-parsing for the claim would cost on the hot path.
     *
     * @throws ApiException with {@link ErrorCode#TOKEN_EXPIRED} or {@link ErrorCode#TOKEN_INVALID}
     */
    public ParsedToken parseDetailed(String token) {
        Claims claims = parseClaims(token);

        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "Wrong token type for this endpoint");
        }

        PrabhixPrincipal principal = new PrabhixPrincipal(
                uuid(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                uuid(claims.get(CLAIM_ORG, String.class)),
                readPermissions(claims),
                uuid(claims.get(CLAIM_SESSION, String.class)),
                Boolean.TRUE.equals(claims.get(CLAIM_PLATFORM_ADMIN, Boolean.class)));

        Date issuedAt = claims.getIssuedAt();
        return new ParsedToken(principal, issuedAt == null ? null : issuedAt.toInstant());
    }

    /** The JWT id, used as the deny-list key when a session is revoked mid-TTL. */
    public String tokenId(String token) {
        return parseClaims(token).getId();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.security().jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw ApiException.of(ErrorCode.TOKEN_EXPIRED, "Your session has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "That token is not valid");
        }
    }

    private Set<Permission> readPermissions(Claims claims) {
        Object raw = claims.get(CLAIM_PERMISSIONS);
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        // Unknown codes are dropped rather than fatal, so removing a permission from the
        // enum does not invalidate every token already in the wild.
        return list.stream()
                .map(String::valueOf)
                .map(Permission::parse)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toUnmodifiableSet());
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "That token is not valid");
        }
    }

    /** @param expiresInSeconds relative TTL, which browsers find easier to act on than an instant */
    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }

    /**
     * @param issuedAt the {@code iat} claim, or null for a token minted without one. JWT dates
     *                 carry second precision, so this is truncated relative to the real issue time.
     */
    public record ParsedToken(PrabhixPrincipal principal, Instant issuedAt) {
    }
}
