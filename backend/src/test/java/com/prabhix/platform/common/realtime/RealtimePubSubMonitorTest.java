package com.prabhix.platform.common.realtime;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RealtimePubSubMonitorTest {

    private static final Duration TOLERANCE = Duration.ofSeconds(90);

    @Mock private StringRedisTemplate redis;
    @Mock private RedisMessageListenerContainer listenerContainer;
    @Mock private RealtimeChannelRegistry registry;

    private SimpleMeterRegistry meterRegistry;
    private long now;
    private RealtimePubSubMonitor monitor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Time is driven by the test rather than the wall clock. Sleeping to cross the threshold
        // made this suite fail only under load, which is the worst way to learn about a race.
        now = 1_000_000L;
        monitor = new RealtimePubSubMonitor(
                redis, listenerContainer, registry, meterRegistry, TOLERANCE, () -> now);
        monitor.start();
    }

    /** The nonce is what separates "our subscription works" from "something is on the channel". */
    @Test
    void theProbeStaysHealthyWhileItsOwnNonceKeepsComingBack() {
        monitor.probe();
        deliver(lastPublishedNonce());

        // Less than the tolerance, which is what a healthy tick interval looks like.
        now += TOLERANCE.toMillis() / 2;
        monitor.probe();
        deliver(lastPublishedNonce());

        assertTrue(monitor.isHealthy());
        verify(registry, never()).resubscribeAll();
    }

    /**
     * The failure this class exists for: a subscription still registered and still apparently
     * connected, receiving nothing.
     */
    @Test
    void subscriptionsAreRebuiltWhenTheProbeStopsReturning() {
        monitor.probe();

        now += TOLERANCE.toMillis() + 1;
        monitor.probe();

        assertFalse(monitor.isHealthy());
        verify(registry).resubscribeAll();
        // The probe's own subscription is pinned to a node too, so it is rebuilt alongside.
        verify(listenerContainer).removeMessageListener(eq(monitor), any(Topic.class));
        verify(listenerContainer, atLeastOnce()).addMessageListener(eq(monitor), any(Topic.class));
        assertTrue(meterRegistry.counter("prabhix.realtime.pubsub.recoveries").count() >= 1.0);
    }

    @Test
    void aStrayMessageOnTheProbeChannelDoesNotKeepTheCheckGreen() {
        monitor.probe();
        deliver("not-the-nonce-we-sent");

        now += TOLERANCE.toMillis() + 1;
        monitor.probe();

        assertFalse(monitor.isHealthy());
        verify(registry).resubscribeAll();
    }

    @Test
    void recoveringOnceDoesNotKeepRecoveringOnEveryTick() {
        monitor.probe();
        now += TOLERANCE.toMillis() + 1;
        monitor.probe();

        // Immediately after a rebuild the clock is reset, so the next tick has nothing to report.
        monitor.probe();

        verify(registry).resubscribeAll();
    }

    /** Health comes back on its own once the probe completes again, without an operator. */
    @Test
    void healthRecoversWhenTheProbeStartsReturningAgain() {
        monitor.probe();
        now += TOLERANCE.toMillis() + 1;
        monitor.probe();
        assertFalse(monitor.isHealthy());

        deliver(lastPublishedNonce());

        assertTrue(monitor.isHealthy());
    }

    /** An unreachable cache is reported by every other Redis caller; this must not add a crash. */
    @Test
    void aFailedPublishIsLoggedRatherThanThrown() {
        doThrow(new IllegalStateException("cache unreachable"))
                .when(redis).convertAndSend(eq(RealtimePubSubMonitor.PROBE_CHANNEL), any());

        monitor.probe();

        assertTrue(monitor.isHealthy());
    }

    private String lastPublishedNonce() {
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(redis, atLeastOnce())
                .convertAndSend(eq(RealtimePubSubMonitor.PROBE_CHANNEL), published.capture());
        return published.getValue().toString();
    }

    private void deliver(String body) {
        monitor.onMessage(
                new DefaultMessage(
                        RealtimePubSubMonitor.PROBE_CHANNEL.getBytes(StandardCharsets.UTF_8),
                        body.getBytes(StandardCharsets.UTF_8)),
                null);
    }
}
