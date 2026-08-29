package com.prabhix.platform.security.tenant;

import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpersonationAuditorTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private StructuredEventLogger eventLogger;

    private ImpersonationAuditor auditor;

    private final UUID admin = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();
    private final UUID homeOrg = UUID.randomUUID();
    private final UUID customerOrg = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        auditor = new ImpersonationAuditor(redis, eventLogger);
    }

    private void firstInWindow(boolean first) {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(first);
    }

    @Test
    @DisplayName("reading a customer organization is recorded")
    void recordsAccessToAnotherOrg() {
        firstInWindow(true);

        auditor.recordAccess(admin, session, homeOrg, customerOrg);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.captor();
        verify(eventLogger).logNow(eq(LogEventCode.SECURITY_TENANT_IMPERSONATED), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("adminUserId", admin)
                .containsEntry("viewedOrganizationId", customerOrg)
                .containsEntry("homeOrganizationId", homeOrg);
    }

    @Test
    @DisplayName("working in your own organization is not impersonation")
    void ignoresOwnOrg() {
        auditor.recordAccess(admin, session, homeOrg, homeOrg);

        verify(eventLogger, never()).logNow(any(), any());
    }

    @Test
    @DisplayName("a support session produces one entry, not one per request")
    void deduplicatesWithinTheWindow() {
        // The filter this runs from sees every request. Recording each one would bury the
        // customer's own log under an afternoon of support traffic and make the fact that staff
        // were present harder to notice, not easier.
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true, false, false, false);

        for (int i = 0; i < 4; i++) {
            auditor.recordAccess(admin, session, homeOrg, customerOrg);
        }

        verify(eventLogger, times(1)).logNow(any(), any());
    }

    @Test
    @DisplayName("two organizations in one session are recorded separately")
    void separateKeyPerOrganization() {
        UUID otherCustomer = UUID.randomUUID();
        ArgumentCaptor<String> keys = ArgumentCaptor.captor();
        when(valueOps.setIfAbsent(keys.capture(), anyString(), any(Duration.class))).thenReturn(true);

        auditor.recordAccess(admin, session, homeOrg, customerOrg);
        auditor.recordAccess(admin, session, homeOrg, otherCustomer);

        verify(eventLogger, times(2)).logNow(any(), any());
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("when the dedupe store is down it records rather than dropping the access")
    void failsOpenTowardsRecording() {
        // A noisy audit trail can be filtered later; a missing one cannot be reconstructed.
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        auditor.recordAccess(admin, session, homeOrg, customerOrg);

        verify(eventLogger).logNow(eq(LogEventCode.SECURITY_TENANT_IMPERSONATED), any());
    }

    @Test
    @DisplayName("a failure to record never denies the admin their request")
    void loggingFailureDoesNotPropagate() {
        // This runs on the authentication path. Throwing here would turn an observability outage
        // into a support team that cannot sign in.
        firstInWindow(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("writer down"))
                .when(eventLogger).logNow(any(), any());

        assertThatCode(() -> auditor.recordAccess(admin, session, homeOrg, customerOrg))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no organization named means nothing to record")
    void ignoresNullViewedOrg() {
        auditor.recordAccess(admin, session, homeOrg, null);

        verify(eventLogger, never()).logNow(any(), any());
    }
}
