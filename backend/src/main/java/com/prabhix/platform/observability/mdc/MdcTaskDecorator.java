package com.prabhix.platform.observability.mdc;

import com.prabhix.platform.observability.context.CorrelationContext;
import org.springframework.core.task.TaskDecorator;

/**
 * Copies MDC from the submitting thread into {@code @Async} worker threads.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        var context = CorrelationContext.capture();
        return () -> CorrelationContext.runWith(context, runnable);
    }
}
