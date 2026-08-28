package com.prabhix.platform.security.jwt;

import com.prabhix.platform.config.PrabhixProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Immediate revocation for tokens that have not expired yet.
 *
 * <p>Access tokens are self-contained, which is what keeps the hot path fast, but it also
 * means a logout or a role change cannot invalidate one on its own. Entries here live only
 * as long as the access-token TTL, so the list stays small — it holds "recently revoked",
 * not "all revoked ever".
 *
 * <p>Redis being unavailable must not lock everyone out, so lookups fail open and log. The
 * exposure is bounded by the 15-minute TTL, which is a better trade than a total outage.
 */
@Slf4j
@Component
public class TokenDenyList {

    private static final String SESSION_KEY = "pbx:deny:session:";
    private static final String USER_KEY = "pbx:deny:user:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public TokenDenyList(StringRedisTemplate redis, PrabhixProperties properties) {
        this.redis = redis;
        this.ttl = properties.security().jwt().accessTokenTtl();
    }

    /** Revokes one device session, e.g. on logout or "sign out this device". */
    public void revokeSession(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        write(SESSION_KEY + sessionId);
    }

    /**
     * Revokes every session for a user: password change, role change, or being removed from
     * an organization. Cheaper and safer than enumerating their sessions.
     */
    public void revokeUser(UUID userId) {
        if (userId == null) {
            return;
        }
        write(USER_KEY + userId);
    }

    public boolean isRevoked(UUID userId, UUID sessionId) {
        try {
            if (sessionId != null && Boolean.TRUE.equals(redis.hasKey(SESSION_KEY + sessionId))) {
                return true;
            }
            return userId != null && Boolean.TRUE.equals(redis.hasKey(USER_KEY + userId));
        } catch (RuntimeException ex) {
            log.warn("Deny-list check failed, allowing the request through: {}", ex.getMessage());
            return false;
        }
    }

    private void write(String key) {
        try {
            redis.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException ex) {
            // Logged loudly: a dropped revocation is a real security event, not noise.
            log.error("Could not write deny-list entry {}. Revocation will lag until the token expires.",
                    key, ex);
        }
    }
}
