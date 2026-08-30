package com.prabhix.platform.common.realtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Watches the Redis pub/sub path that every SSE stream depends on, and repairs it when it dies
 * without saying so.
 *
 * <p>Chat, mail and AI streams all publish with {@code convertAndSend} and receive through
 * {@link RealtimeChannelRegistry}, which holds one subscribe connection per channel. Against a
 * cluster — and ElastiCache Serverless is always cluster mode — that connection is pinned to a
 * single node, and a node can be retired as the cache scales. The client is not reliably told.
 * The listener still exists, the socket looks fine, and messages simply stop arriving: chat goes
 * quiet, every health check stays green, and nothing appears in the logs.
 *
 * <p>A one-off test cannot catch that, because it only happens when the cache decides to scale.
 * So this publishes a probe on a timer and checks that it comes back. If it stops coming back,
 * the subscriptions are torn down and rebuilt, which is what forces fresh connections.
 *
 * <p>Deliberately not a {@code HealthIndicator}: the container healthcheck reads
 * {@code /actuator/health}, so reporting DOWN here would have Docker restart a process that is
 * serving traffic perfectly well, turning a degraded stream into an outage. A loud log line and a
 * metric are the right signal, and the repair happens without anyone reading either.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "prabhix.realtime.pubsub-monitor.enabled", matchIfMissing = true)
public class RealtimePubSubMonitor implements MessageListener {

    static final String PROBE_CHANNEL = "pbx:realtime:probe";

    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listenerContainer;
    private final RealtimeChannelRegistry registry;
    private final MeterRegistry meterRegistry;
    private final ChannelTopic topic = new ChannelTopic(PROBE_CHANNEL);
    private final Duration tolerance;

    private final LongSupplier clock;
    private final AtomicReference<String> outstandingProbe = new AtomicReference<>();
    private final AtomicLong lastReturnedAtMillis = new AtomicLong();
    private final AtomicInteger healthy = new AtomicInteger(1);
    private Counter recoveries;

    /**
     * Annotated because the second constructor exists for tests, and with two candidates Spring
     * will not guess — the same reason {@link RealtimeChannelRegistry} carries this annotation.
     */
    @Autowired
    public RealtimePubSubMonitor(StringRedisTemplate redis,
                                RedisMessageListenerContainer listenerContainer,
                                RealtimeChannelRegistry registry,
                                MeterRegistry meterRegistry,
                                @Value("${prabhix.realtime.pubsub-monitor.tolerance:PT90S}")
                                Duration tolerance) {
        this(redis, listenerContainer, registry, meterRegistry, tolerance, System::currentTimeMillis);
    }

    /** Time is injectable so the silence threshold can be tested without sleeping through it. */
    RealtimePubSubMonitor(StringRedisTemplate redis,
                          RedisMessageListenerContainer listenerContainer,
                          RealtimeChannelRegistry registry,
                          MeterRegistry meterRegistry,
                          Duration tolerance,
                          LongSupplier clock) {
        this.redis = redis;
        this.listenerContainer = listenerContainer;
        this.registry = registry;
        this.meterRegistry = meterRegistry;
        this.tolerance = tolerance;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        // Seeded to now, not zero: otherwise the first tick fires before any probe has had time to
        // make the round trip and reports a healthy cache as broken on every startup.
        lastReturnedAtMillis.set(clock.getAsLong());
        recoveries = Counter.builder("prabhix.realtime.pubsub.recoveries")
                .description("Times the realtime pub/sub subscriptions were rebuilt after the probe stopped returning")
                .register(meterRegistry);
        Gauge.builder("prabhix.realtime.pubsub.healthy", healthy, AtomicInteger::get)
                .description("1 when the realtime pub/sub probe is completing, 0 while it is not")
                .register(meterRegistry);
        listenerContainer.addMessageListener(this, topic);
    }

    @PreDestroy
    void stop() {
        listenerContainer.removeMessageListener(this, topic);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String received = new String(message.getBody(), StandardCharsets.UTF_8);
        // Compared against the nonce we sent so a stray publisher on the same channel cannot keep
        // the check green while our own subscription is dead.
        if (received.equals(outstandingProbe.get())) {
            lastReturnedAtMillis.set(clock.getAsLong());
            if (healthy.getAndSet(1) == 0) {
                log.info("Realtime pub/sub probe is completing again");
            }
        }
    }

    @Scheduled(fixedDelayString = "${prabhix.realtime.pubsub-monitor.interval:PT20S}")
    void probe() {
        long silentFor = clock.getAsLong() - lastReturnedAtMillis.get();
        if (silentFor > tolerance.toMillis()) {
            recover(silentFor);
        }
        String nonce = UUID.randomUUID().toString();
        outstandingProbe.set(nonce);
        try {
            redis.convertAndSend(PROBE_CHANNEL, nonce);
        } catch (RuntimeException ex) {
            // A cache that is entirely unreachable is not this class's story to tell — the
            // connection pool and every other Redis caller will be reporting it. Logged at warn so
            // the two failure modes stay distinguishable in the logs.
            log.warn("Realtime pub/sub probe could not be published: {}", ex.getMessage());
        }
    }

    private void recover(long silentForMillis) {
        healthy.set(0);
        recoveries.increment();
        // Error, not warn: SSE has silently stopped delivering, which users experience as chat and
        // mail being broken, and nothing else in the system will say so.
        log.error("Realtime pub/sub probe has not returned for {} ms (tolerance {} ms). "
                        + "Rebuilding subscriptions — SSE streams were not delivering.",
                silentForMillis, tolerance.toMillis());
        try {
            listenerContainer.removeMessageListener(this, topic);
            listenerContainer.addMessageListener(this, topic);
            int rebuilt = registry.resubscribeAll();
            log.warn("Rebuilt the probe subscription and {} realtime channel subscriptions", rebuilt);
        } catch (RuntimeException ex) {
            log.error("Could not rebuild realtime subscriptions: {}", ex.getMessage(), ex);
        }
        // Reset the clock either way. Without this a failed rebuild would re-enter recovery on
        // every tick and bury the logs while doing nothing new.
        lastReturnedAtMillis.set(clock.getAsLong());
    }

    boolean isHealthy() {
        return healthy.get() == 1;
    }
}
