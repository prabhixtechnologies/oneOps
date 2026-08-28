package com.prabhix.platform.observability.mdc;

import com.prabhix.platform.observability.context.CorrelationContext;
import com.prabhix.platform.observability.context.CorrelationIdSanitizer;
import com.prabhix.platform.observability.context.MdcKeys;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Ensures platform thread pools propagate MDC into scheduled and async workers.
 */
@Component
public class MdcExecutorPostProcessor implements BeanPostProcessor {

    private final TaskDecorator decorator = new MdcTaskDecorator();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ThreadPoolTaskExecutor executor) {
            executor.setTaskDecorator(decorator);
        }
        if (bean instanceof ThreadPoolTaskScheduler scheduler) {
            scheduler.setTaskDecorator(runnable -> decorator.decorate(wrapScheduled(runnable)));
        }
        return bean;
    }

    private Runnable wrapScheduled(Runnable runnable) {
        return () -> {
            if (CorrelationContext.correlationId().isEmpty()) {
                org.slf4j.MDC.put(MdcKeys.CORRELATION_ID, CorrelationIdSanitizer.generate());
            }
            runnable.run();
        };
    }
}
