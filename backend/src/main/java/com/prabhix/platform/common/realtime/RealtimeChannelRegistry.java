package com.prabhix.platform.common.realtime;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Component
public class RealtimeChannelRegistry {

    private final RedisMessageListenerContainer listenerContainer;
    private final SseHeartbeatScheduler heartbeatScheduler;
    private final MeterRegistry meterRegistry;
    private final Supplier<SseEmitter> emitterFactory;
    private final ConcurrentHashMap<String, ChannelState> channels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, ChannelState> emitterOwners = new ConcurrentHashMap<>();
    private final AtomicLong connectionCount = new AtomicLong();

    /**
     * The second constructor exists only so tests can inject a fake emitter, and with two
     * candidates Spring will not guess: without this annotation it looks for a no-arg
     * constructor and the context fails to start.
     */
    @Autowired
    public RealtimeChannelRegistry(RedisMessageListenerContainer listenerContainer,
                                   SseHeartbeatScheduler heartbeatScheduler,
                                   MeterRegistry meterRegistry) {
        this(listenerContainer, heartbeatScheduler, meterRegistry, () -> new SseEmitter(0L));
    }

    RealtimeChannelRegistry(RedisMessageListenerContainer listenerContainer,
                            SseHeartbeatScheduler heartbeatScheduler,
                            MeterRegistry meterRegistry,
                            Supplier<SseEmitter> emitterFactory) {
        this.listenerContainer = listenerContainer;
        this.heartbeatScheduler = heartbeatScheduler;
        this.meterRegistry = meterRegistry;
        this.emitterFactory = emitterFactory;
    }

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("prabhix.realtime.sse.connections", connectionCount, AtomicLong::get)
                .description("Active SSE connections managed by the shared realtime registry")
                .register(meterRegistry);
    }

    public SseEmitter subscribe(String redisChannel) {
        ChannelState state = channels.computeIfAbsent(redisChannel, ChannelState::new);
        synchronized (state) {
            ensureRedisSubscription(state);
            SseEmitter emitter = emitterFactory.get();
            state.emitters.add(emitter);
            emitterOwners.put(emitter, state);
            connectionCount.incrementAndGet();
            Runnable cleanup = () -> release(state, emitter);
            emitter.onCompletion(cleanup);
            emitter.onTimeout(cleanup);
            emitter.onError(ex -> cleanup.run());
            heartbeatScheduler.schedule(emitter, cleanup);
            return emitter;
        }
    }

    public void fanOutLocal(String redisChannel, String body) {
        ChannelState state = channels.get(redisChannel);
        if (state == null) {
            return;
        }
        broadcast(state, body);
    }

    int activeConnections() {
        return (int) connectionCount.get();
    }

    int subscribedChannelCount() {
        return channels.size();
    }

    void detach(String redisChannel, SseEmitter emitter) {
        ChannelState state = emitterOwners.get(emitter);
        if (state != null) {
            release(state, emitter);
        }
    }

    @PreDestroy
    void shutdown() {
        for (ChannelState state : channels.values()) {
            synchronized (state) {
                if (state.subscribed) {
                    listenerContainer.removeMessageListener(state.listener, state.topic);
                    state.subscribed = false;
                }
            }
        }
        channels.clear();
        emitterOwners.clear();
        connectionCount.set(0);
    }

    private void ensureRedisSubscription(ChannelState state) {
        if (!state.subscribed) {
            listenerContainer.addMessageListener(state.listener, state.topic);
            state.subscribed = true;
        }
    }

    private void release(ChannelState state, SseEmitter emitter) {
        synchronized (state) {
            heartbeatScheduler.cancel(emitter);
            if (!state.emitters.remove(emitter)) {
                return;
            }
            emitterOwners.remove(emitter);
            connectionCount.decrementAndGet();
            if (state.emitters.isEmpty()) {
                if (state.subscribed) {
                    listenerContainer.removeMessageListener(state.listener, state.topic);
                    state.subscribed = false;
                }
                channels.remove(state.redisChannel, state);
            }
        }
    }

    private void broadcast(ChannelState state, String body) {
        for (SseEmitter emitter : state.emitters) {
            try {
                emitter.send(SseEmitter.event().name("message").data(body));
            } catch (IOException ex) {
                release(state, emitter);
            }
        }
    }

    private final class ChannelState {
        private final String redisChannel;
        private final ChannelTopic topic;
        private final MessageListener listener;
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private volatile boolean subscribed;

        private ChannelState(String redisChannel) {
            this.redisChannel = redisChannel;
            this.topic = new ChannelTopic(redisChannel);
            this.listener = (message, pattern) -> {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                broadcast(this, payload);
            };
        }
    }
}
