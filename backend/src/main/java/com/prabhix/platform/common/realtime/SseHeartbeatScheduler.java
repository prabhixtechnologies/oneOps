package com.prabhix.platform.common.realtime;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
class SseHeartbeatScheduler {

    private static final long INTERVAL_SECONDS = 15;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private final Map<SseEmitter, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    void schedule(SseEmitter emitter, Runnable onDead) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (Exception ex) {
                onDead.run();
                cancel(emitter);
            }
            // Fires immediately, then on the interval. The first beat is what commits the response:
            // until something is written, the client's fetch() has no headers to resolve against, so
            // an idle stream looked like a stalled connection for a full interval after every open
            // and every reconnect — and the console reported "connecting" for that whole time.
        }, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
        tasks.put(emitter, future);
    }

    void cancel(SseEmitter emitter) {
        ScheduledFuture<?> future = tasks.remove(emitter);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        tasks.clear();
    }
}
