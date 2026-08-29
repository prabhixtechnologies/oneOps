package com.prabhix.platform.security.tenant;

import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records that a platform admin acted inside a customer organization.
 *
 * <p>Staff can name any organization in {@code X-Prabhix-Org} and the request is served against
 * that tenant's data without any membership check. That is how support is meant to work, but it
 * previously left no trace: the code carried a comment claiming an interceptor audited it, and no
 * such interceptor existed. There was no way to answer "who read this customer's mailbox".
 *
 * <h2>Why it is deduplicated</h2>
 *
 * <p>The obvious place to record this is the authentication filter, which runs on every single
 * request. Writing a row each time would bury the customer's own log under thousands of entries
 * from one afternoon of support work, and make the useful signal — that staff were in here at all —
 * harder to see rather than easier.
 *
 * <p>So the first request in each window writes one event and the rest are silent. The window is
 * long enough to collapse a working session into a single row and short enough that a return visit
 * hours later shows up as its own.
 *
 * <h2>Why it fails open</h2>
 *
 * <p>If Redis is unreachable the deduplication cannot be consulted, and the choice is between
 * writing every request or writing none. It writes: a noisy audit trail is recoverable, a missing
 * one is not. And in no case does an auditing failure change the caller's own outcome — this runs
 * on the authentication path, where throwing would deny access to a legitimate admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImpersonationAuditor {

    private static final String KEY_PREFIX = "pbx:imp:";
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final StructuredEventLogger eventLogger;

    /**
     * @param adminUserId the staff member acting
     * @param sessionId   their device session, so two browsers are recorded separately
     * @param homeOrgId   the organization their token belongs to; may be null
     * @param viewedOrgId the organization they asked to act in
     */
    public void recordAccess(UUID adminUserId, UUID sessionId, UUID homeOrgId, UUID viewedOrgId) {
        if (viewedOrgId == null || viewedOrgId.equals(homeOrgId)) {
            // Acting in their own organization is ordinary use, not impersonation.
            return;
        }

        try {
            if (!isFirstInWindow(adminUserId, sessionId, viewedOrgId)) {
                return;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("adminUserId", adminUserId);
            payload.put("viewedOrganizationId", viewedOrgId);
            payload.put("homeOrganizationId", homeOrgId);
            payload.put("sessionId", sessionId);
            payload.put("windowMinutes", WINDOW.toMinutes());

            // logNow rather than log: this runs in a filter, outside any transaction that would
            // later commit and flush a deferred write.
            eventLogger.logNow(LogEventCode.SECURITY_TENANT_IMPERSONATED, payload);
        } catch (RuntimeException ex) {
            log.error("Failed to record tenant impersonation by {} into {}: {}",
                    adminUserId, viewedOrgId, ex.getMessage(), ex);
        }
    }

    /**
     * True when nothing has claimed this key yet, using SETNX so two concurrent requests cannot
     * both decide they are first.
     */
    private boolean isFirstInWindow(UUID adminUserId, UUID sessionId, UUID viewedOrgId) {
        String key = KEY_PREFIX + adminUserId + ":" + sessionId + ":" + viewedOrgId;
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(key, "1", WINDOW);
            return claimed == null || claimed;
        } catch (RuntimeException ex) {
            log.warn("Impersonation dedupe unavailable, recording this access: {}", ex.getMessage());
            return true;
        }
    }
}
