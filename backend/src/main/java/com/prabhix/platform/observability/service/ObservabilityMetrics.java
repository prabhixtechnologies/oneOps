package com.prabhix.platform.observability.service;

import com.prabhix.platform.observability.taxonomy.LogEventCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class ObservabilityMetrics {

    private final MeterRegistry registry;
    private final AtomicLong mailOutboxPending = new AtomicLong();
    private final AtomicLong mailOutboxFailed = new AtomicLong();
    private final AtomicLong paymentFailures = new AtomicLong();
    private final AtomicLong aiTokensUsed = new AtomicLong();
    private final AtomicLong chatQueueWaitMs = new AtomicLong();
    private final AtomicLong activeVisitors = new AtomicLong();

    @PostConstruct
    void register() {
        Gauge.builder("prabhix.mail.outbox.pending", mailOutboxPending, AtomicLong::get)
                .description("Pending outbound mail outbox rows")
                .register(registry);
        Gauge.builder("prabhix.mail.outbox.failed", mailOutboxFailed, AtomicLong::get)
                .description("Failed outbound mail outbox rows")
                .register(registry);
        Gauge.builder("prabhix.payment.failures", paymentFailures, AtomicLong::get)
                .description("Recent payment failure count")
                .register(registry);
        Gauge.builder("prabhix.ai.tokens.used", aiTokensUsed, AtomicLong::get)
                .description("AI tokens consumed in current window")
                .register(registry);
        Gauge.builder("prabhix.chat.queue.wait.ms", chatQueueWaitMs, AtomicLong::get)
                .description("Average chat queue wait in milliseconds")
                .register(registry);
        Gauge.builder("prabhix.visitors.active", activeVisitors, AtomicLong::get)
                .description("Currently active visitors")
                .register(registry);

        for (LogEventCode code : LogEventCode.values()) {
            if (code.severity().ordinal() >= com.prabhix.platform.observability.taxonomy.LogSeverity.ERROR.ordinal()) {
                Counter.builder("prabhix.events")
                        .tag("code", code.code())
                        .tag("severity", code.severity().name())
                        .register(registry);
            }
        }
    }

    public void setMailOutboxPending(long value) {
        mailOutboxPending.set(value);
    }

    public void setMailOutboxFailed(long value) {
        mailOutboxFailed.set(value);
    }

    public void incrementPaymentFailure() {
        paymentFailures.incrementAndGet();
    }

    public void addAiTokens(long tokens) {
        aiTokensUsed.addAndGet(tokens);
    }

    public void setChatQueueWaitMs(long ms) {
        chatQueueWaitMs.set(ms);
    }

    public void setActiveVisitors(long count) {
        activeVisitors.set(count);
    }

    public void incrementEvent(LogEventCode code) {
        registry.counter("prabhix.events",
                "code", code.code(),
                "severity", code.severity().name(),
                "category", code.category().name()).increment();
    }
}
