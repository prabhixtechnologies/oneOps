package com.prabhix.platform.security.jwt;

import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenDenyListTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private TokenDenyList denyList;

    private final UUID userId = UUID.fromString("0c203b2a-a6b9-40d0-9563-6d3a6b2b8efa");
    private final UUID sessionId = UUID.fromString("0dda02e6-e982-49af-80c8-99df18b33c60");

    private String userKey() {
        return "pbx:deny:user:" + userId;
    }

    private String sessionKey() {
        return "pbx:deny:session:" + sessionId;
    }

    @BeforeEach
    void setUp() {
        denyList = new TokenDenyList(
                redis, TestProperties.withSecurity(TestProperties.security(Duration.ofMinutes(15))));
    }

    /**
     * The regression this class exists to prevent. A user-wide revocation used to store a marker,
     * and the check only asked whether the key existed, so signing in again after a password change
     * was rejected for the rest of the access-token TTL — with the correct new password.
     */
    @Test
    void acceptsATokenIssuedAfterAUserWideRevocation() {
        Instant revokedAt = Instant.now().minusSeconds(30);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(userKey())).thenReturn(Long.toString(revokedAt.toEpochMilli()));

        assertFalse(denyList.isRevoked(userId, null, null, revokedAt.plusSeconds(5)));
    }

    @Test
    void rejectsATokenIssuedBeforeAUserWideRevocation() {
        Instant revokedAt = Instant.now();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(userKey())).thenReturn(Long.toString(revokedAt.toEpochMilli()));

        assertTrue(denyList.isRevoked(userId, null, null, revokedAt.minusSeconds(60)));
    }

    /**
     * Session ids are not reissued, so a revoked session must stay revoked whatever the token says
     * about when it was minted. Only the user scope compares timestamps.
     */
    @Test
    void rejectsARevokedSessionRegardlessOfIssueTime() {
        when(redis.hasKey(sessionKey())).thenReturn(true);

        assertTrue(denyList.isRevoked(userId, sessionId, null, Instant.now().plusSeconds(300)));
    }

    @Test
    void allowsWhenThereIsNoEntryAtAll() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(userKey())).thenReturn(null);

        assertFalse(denyList.isRevoked(userId, null, null, Instant.now()));
    }

    /**
     * Entries written by the previous build hold "1" rather than an instant. Those cannot be
     * ordered against a token, so they deny — matching the old behaviour until they expire.
     */
    @Test
    void rejectsAgainstALegacyMarkerEntry() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(userKey())).thenReturn("1");

        assertTrue(denyList.isRevoked(userId, null, null, Instant.now()));
    }

    @Test
    void rejectsATokenWithNoIssuedAtClaim() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(userKey())).thenReturn(Long.toString(Instant.now().toEpochMilli()));

        assertTrue(denyList.isRevoked(userId, null, null, null));
    }

    /** Redis being down must not lock every user out; the exposure is capped by the token TTL. */
    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertFalse(denyList.isRevoked(userId, null, null, Instant.now()));
    }

    @Test
    void revokeUserStoresTheRevocationInstantSoLaterTokensCanBeToldApart() {
        when(redis.opsForValue()).thenReturn(valueOps);
        Instant before = Instant.now();

        denyList.revokeUser(userId);

        // The stored value has to parse as an epoch-milli no earlier than the call, or isRevoked
        // treats it as a legacy marker and denies everything.
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(userKey()), value.capture(), eq(Duration.ofMinutes(15)));
        assertTrue(Long.parseLong(value.getValue()) >= before.toEpochMilli());
    }
}
