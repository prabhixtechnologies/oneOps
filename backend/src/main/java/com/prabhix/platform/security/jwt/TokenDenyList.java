package com.prabhix.platform.security.jwt;

import com.prabhix.identity.client.IdentityToken;
import com.prabhix.platform.config.PrabhixProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Immediate revocation for identity tokens that have not expired yet.
 *
 * <p>Identity's access tokens are self-contained, which is what keeps the hot path fast, but it also
 * means a logout here or a role change cannot invalidate one on its own. Entries live only as long
 * as the access-token lifetime, so the list stays small — it holds "recently revoked", not "all
 * revoked ever".
 *
 * <p>Three scopes, deliberately different:
 *
 * <ul>
 *   <li><b>Session</b> entries deny outright. Identity's {@code sid} is fixed for the life of that
 *       sign-in and a new sign-in mints a new one, so there is no way for a legitimate later token to
 *       carry a revoked session id.
 *   <li><b>Token</b> entries deny one {@code jti} outright, for a token that names no session.
 *   <li><b>User</b> entries deny only tokens issued <em>before</em> the revocation. They must not deny
 *       outright: signing in again is the expected response to a revocation, and the new token would
 *       otherwise be rejected for the rest of the TTL.
 * </ul>
 *
 * <p>Redis being unavailable must not lock everyone out, so lookups fail open and log. The exposure
 * is bounded by the TTL, which is a better trade than a total outage.
 */
@Slf4j
@Component
public class TokenDenyList {

    private static final String SESSION_KEY = "pbx:deny:session:";
    private static final String TOKEN_KEY = "pbx:deny:token:";
    private static final String USER_KEY = "pbx:deny:user:";

    /** 2001-09-09. Below this a stored value is a marker from an older build, not a timestamp. */
    private static final long MIN_PLAUSIBLE_EPOCH_MILLIS = 1_000_000_000_000L;

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Counter redisUnavailable;

    public TokenDenyList(StringRedisTemplate redis, PrabhixProperties properties) {
        this(redis, properties, new SimpleMeterRegistry());
    }

    @Autowired
    public TokenDenyList(StringRedisTemplate redis, PrabhixProperties properties, MeterRegistry meters) {
        this.redis = redis;
        this.ttl = properties.security().denyListTtl();
        this.redisUnavailable = Counter.builder("prabhix.identity.deny_list.redis_unavailable")
                .description("Deny-list Redis outage: lookups fail open; writes are dropped")
                .register(meters);
    }

    /** Revokes one identity session, e.g. on logout from a console. */
    public void revokeSession(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        write(SESSION_KEY + sessionId, "1");
    }

    /** Revokes a single token by its {@code jti}, for tokens that carry no session id. */
    public void revokeToken(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        write(TOKEN_KEY + tokenId, "1");
    }

    /**
     * Revokes the token a caller presented: its session when it has one, otherwise the token itself.
     * What logout does, so that the bearer just used cannot be replayed for the rest of its life.
     */
    public void revoke(IdentityToken token) {
        if (token.sessionId() != null) {
            revokeSession(token.sessionId());
        } else {
            revokeToken(token.tokenId());
        }
    }

    /**
     * Revokes every session for a user: role change, being removed from an organization, break glass.
     * Cheaper and safer than enumerating their sessions.
     *
     * <p>Stores the revocation instant rather than a marker, so {@link #isRevoked} can let a
     * subsequent sign-in through. Storing a marker made a revocation lock the user out of their own
     * new session until the entry expired.
     */
    public void revokeUser(UUID userId) {
        if (userId == null) {
            return;
        }
        write(USER_KEY + userId, Long.toString(Instant.now().toEpochMilli()));
    }

    /** Whether a verified identity token has been revoked here since it was issued. */
    public boolean isRevoked(IdentityToken token) {
        return isRevoked(token.subject(), token.sessionId(), token.tokenId(), token.issuedAt());
    }

    /**
     * @param issuedAt the token's {@code iat}. A null value is treated as "older than any
     *                 revocation", because a token we cannot date is not one to trust against a
     *                 pending revocation.
     */
    public boolean isRevoked(UUID userId, UUID sessionId, String tokenId, Instant issuedAt) {
        try {
            if (sessionId != null && Boolean.TRUE.equals(redis.hasKey(SESSION_KEY + sessionId))) {
                return true;
            }
            if (tokenId != null && !tokenId.isBlank()
                    && Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY + tokenId))) {
                return true;
            }
            if (userId == null) {
                return false;
            }
            String revokedAt = redis.opsForValue().get(USER_KEY + userId);
            if (revokedAt == null) {
                return false;
            }
            return issuedBefore(issuedAt, revokedAt);
        } catch (RuntimeException ex) {
            redisUnavailable.increment();
            log.warn("Deny-list check failed, allowing the request through: {}", ex.getMessage());
            return false;
        }
    }

    private boolean issuedBefore(Instant issuedAt, String revokedAt) {
        if (issuedAt == null) {
            return true;
        }
        long revokedAtMillis;
        try {
            revokedAtMillis = Long.parseLong(revokedAt);
        } catch (NumberFormatException ex) {
            return true;
        }
        // An older build stored the marker "1", which parses as an instant in 1970 and would
        // therefore deny nothing. Anything below the floor is such a marker, not a revocation this
        // build wrote, so deny — matching the previous behaviour until the entry ages out.
        if (revokedAtMillis < MIN_PLAUSIBLE_EPOCH_MILLIS) {
            return true;
        }
        // Not strictly before: iat has second precision, so a token minted in the same second as
        // the revocation cannot be ordered against it. Denying is the fail-safe direction, and
        // costs at most a retry one second later.
        return issuedAt.toEpochMilli() <= revokedAtMillis;
    }

    private void write(String key, String value) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (RuntimeException ex) {
            redisUnavailable.increment();
            log.error("Could not write deny-list entry {}. Revocation will lag until the token expires.",
                    key, ex);
        }
    }
}
