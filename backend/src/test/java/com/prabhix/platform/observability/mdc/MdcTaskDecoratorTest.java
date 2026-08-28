package com.prabhix.platform.observability.mdc;

import com.prabhix.platform.observability.context.CorrelationContext;
import com.prabhix.platform.observability.context.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdcTaskDecoratorTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void propagatesMdcIntoAsyncThread() throws Exception {
        MDC.put(MdcKeys.CORRELATION_ID, "corr-async-test");
        MDC.put(MdcKeys.USER_ID, "user-1");

        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> seen = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(decorator.decorate(() -> {
                seen.set(MDC.get(MdcKeys.CORRELATION_ID));
                latch.countDown();
            }));
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals("corr-async-test", seen.get());
    }

    @Test
    void restorePreviousContextAfterRun() {
        MDC.put(MdcKeys.CORRELATION_ID, "outer");
        CorrelationContext.runWith(java.util.Map.of(MdcKeys.CORRELATION_ID, "inner"), () ->
                assertEquals("inner", MDC.get(MdcKeys.CORRELATION_ID)));
        assertEquals("outer", MDC.get(MdcKeys.CORRELATION_ID));
    }
}
