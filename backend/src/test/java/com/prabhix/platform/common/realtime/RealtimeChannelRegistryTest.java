package com.prabhix.platform.common.realtime;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RealtimeChannelRegistryTest {

    @Mock
    private RedisMessageListenerContainer listenerContainer;

    private SseHeartbeatScheduler heartbeatScheduler;
    private SimpleMeterRegistry meterRegistry;
    private RealtimeChannelRegistry registry;
    private final List<RecordingSseEmitter> createdEmitters = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        heartbeatScheduler = new SseHeartbeatScheduler();
        meterRegistry = new SimpleMeterRegistry();
        registry = new RealtimeChannelRegistry(
                listenerContainer,
                heartbeatScheduler,
                meterRegistry,
                () -> {
                    RecordingSseEmitter emitter = new RecordingSseEmitter();
                    createdEmitters.add(emitter);
                    return emitter;
                });
        registry.registerMetrics();
    }

    @Test
    void firstSubscriberSubscribesToRedis() {
        String channel = "mail:stream:" + UUID.randomUUID();

        registry.subscribe(channel);

        verify(listenerContainer).addMessageListener(any(MessageListener.class), eq(new ChannelTopic(channel)));
        assertThat(registry.activeConnections()).isEqualTo(1);
        assertThat(registry.subscribedChannelCount()).isEqualTo(1);
    }

    @Test
    void secondSubscriberReusesRedisSubscription() {
        String channel = "mail:stream:" + UUID.randomUUID();

        registry.subscribe(channel);
        registry.subscribe(channel);

        verify(listenerContainer, times(1)).addMessageListener(any(MessageListener.class), eq(new ChannelTopic(channel)));
        assertThat(registry.activeConnections()).isEqualTo(2);
        assertThat(registry.subscribedChannelCount()).isEqualTo(1);
    }

    @Test
    void lastUnsubscriberRemovesRedisSubscription() {
        String channel = "mail:stream:" + UUID.randomUUID();
        SseEmitter first = registry.subscribe(channel);
        SseEmitter second = registry.subscribe(channel);

        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        verify(listenerContainer).addMessageListener(listenerCaptor.capture(), eq(new ChannelTopic(channel)));

        registry.detach(channel, first);
        verify(listenerContainer, never()).removeMessageListener(any(MessageListener.class), any(Topic.class));

        registry.detach(channel, second);
        Topic topic = new ChannelTopic(channel);
        verify(listenerContainer).removeMessageListener(listenerCaptor.getValue(), topic);
        assertThat(registry.activeConnections()).isZero();
        assertThat(registry.subscribedChannelCount()).isZero();
    }

    @Test
    void fanOutLocal_reachesAllLocalEmitters() {
        String channel = "chat:stream:org:" + UUID.randomUUID();
        registry.subscribe(channel);
        registry.subscribe(channel);

        registry.fanOutLocal(channel, "{\"type\":\"message.new\"}");

        assertThat(createdEmitters).hasSize(2);
        assertThat(createdEmitters).allMatch(e -> e.messages.size() == 1);
    }

    @Test
    void subscribe_sendsHeartbeatImmediatelyNotAfterAnInterval() throws Exception {
        String channel = "mail:stream:" + UUID.randomUUID();

        RecordingSseEmitter emitter = (RecordingSseEmitter) registry.subscribe(channel);

        // The first write is what commits the HTTP response, so it decides how long the client waits
        // before its fetch() resolves and the stream counts as connected. Deferring it by one
        // interval left the console showing "connecting" for fifteen seconds after every open.
        boolean beat = false;
        for (int i = 0; i < 40 && !beat; i++) {
            synchronized (emitter) {
                beat = !emitter.heartbeats.isEmpty();
            }
            if (!beat) {
                Thread.sleep(50);
            }
        }

        assertThat(beat).as("heartbeat within 2s of subscribing").isTrue();
        assertThat(emitter.messages).isEmpty();
    }

    @Test
    void redisMessage_reachesAllLocalEmitters() {
        String channel = "chat:stream:org:" + UUID.randomUUID();
        registry.subscribe(channel);

        MessageListener listener = captureListener(channel);
        Message message = org.mockito.Mockito.mock(Message.class);
        org.mockito.Mockito.when(message.getBody()).thenReturn("{\"type\":\"redis\"}".getBytes(StandardCharsets.UTF_8));
        listener.onMessage(message, null);

        assertThat(createdEmitters.get(0).messages).hasSize(1);
    }

    @Test
    void disconnectDuringBroadcast_doesNotBreakOtherEmitters() throws Exception {
        String channel = "chat:stream:org:" + UUID.randomUUID();
        RecordingSseEmitter failing = (RecordingSseEmitter) registry.subscribe(channel);
        RecordingSseEmitter healthy = (RecordingSseEmitter) registry.subscribe(channel);
        failing.failOnSend.set(1);

        registry.fanOutLocal(channel, "{\"type\":\"burst\"}");

        assertThat(healthy.messages).hasSize(1);
        assertThat(registry.activeConnections()).isEqualTo(1);
    }

    @Test
    void crossTenantIsolation_orgA_neverReceivesOrgBMessage() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        String channelA = "mail:stream:" + orgA;
        String channelB = "mail:stream:" + orgB;

        registry.subscribe(channelA);
        registry.subscribe(channelB);

        RecordingSseEmitter orgAEmitter = createdEmitters.get(0);
        RecordingSseEmitter orgBEmitter = createdEmitters.get(1);

        registry.fanOutLocal(channelA, "{\"type\":\"mail.thread.updated\",\"org\":\"A\"}");

        assertThat(orgAEmitter.messages).hasSize(1);
        assertThat(orgBEmitter.messages).isEmpty();
    }

    @Test
    void concurrentSubscribeAndBroadcast() throws Exception {
        String channel = "chat:stream:org:" + UUID.randomUUID();
        int threads = 8;
        int iterations = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger received = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        SseEmitter emitter = registry.subscribe(channel);
                        registry.fanOutLocal(channel, "{\"n\":" + j + "}");
                        registry.detach(channel, emitter);
                        received.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(registry.activeConnections()).isZero();
        assertThat(received.get()).isEqualTo(threads * iterations);
        verify(listenerContainer, atLeastOnce()).addMessageListener(any(), eq(new ChannelTopic(channel)));
        verify(listenerContainer, atLeastOnce()).removeMessageListener(any(), eq(new ChannelTopic(channel)));
    }

    private MessageListener captureListener(String channel) {
        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        verify(listenerContainer).addMessageListener(listenerCaptor.capture(), eq(new ChannelTopic(channel)));
        return listenerCaptor.getValue();
    }

    /**
     * Records what was sent rather than merely that something was, and keeps heartbeats apart from
     * messages. The registry schedules a heartbeat on subscribe that now fires immediately, so a fake
     * that counts every send alike reports two events where the test means one, and the fan-out
     * assertions fail for a reason that has nothing to do with fan-out.
     */
    static final class RecordingSseEmitter extends SseEmitter {
        final List<String> messages = new ArrayList<>();
        final List<String> heartbeats = new ArrayList<>();
        final AtomicInteger failOnSend = new AtomicInteger();

        RecordingSseEmitter() {
            super(0L);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            if (failOnSend.get() > 0) {
                failOnSend.decrementAndGet();
                throw new IOException("simulated disconnect");
            }
            StringBuilder rendered = new StringBuilder();
            for (DataWithMediaType chunk : builder.build()) {
                if (chunk.getData() instanceof String text) {
                    rendered.append(text);
                }
            }
            String event = rendered.toString();
            if (event.startsWith("event:heartbeat")) {
                heartbeats.add(event);
            } else {
                messages.add(event);
            }
        }
    }
}
