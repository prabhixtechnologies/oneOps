package com.prabhix.platform.observability.context;

import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Captures and restores MDC correlation fields across thread hops.
 */
public final class CorrelationContext {

    private CorrelationContext() {
    }

    public static Map<String, String> capture() {
        Map<String, String> copy = new HashMap<>();
        for (String key : allKeys()) {
            String value = MDC.get(key);
            if (value != null) {
                copy.put(key, value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public static void restore(Map<String, String> context) {
        MDC.clear();
        if (context != null) {
            context.forEach(MDC::put);
        }
    }

    public static void clear() {
        MDC.clear();
    }

    public static Optional<String> correlationId() {
        return Optional.ofNullable(MDC.get(MdcKeys.CORRELATION_ID));
    }

    public static String currentOrNew() {
        return correlationId().orElseGet(CorrelationIdSanitizer::generate);
    }

    public static void runWith(Map<String, String> context, Runnable work) {
        Map<String, String> previous = capture();
        try {
            restore(context);
            work.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callWith(Map<String, String> context, java.util.function.Supplier<T> work) {
        Map<String, String> previous = capture();
        try {
            restore(context);
            return work.get();
        } finally {
            restore(previous);
        }
    }

    private static String[] allKeys() {
        return new String[]{
                MdcKeys.REQUEST_ID,
                MdcKeys.CORRELATION_ID,
                MdcKeys.TRACE_ID,
                MdcKeys.ORG_ID,
                MdcKeys.USER_ID,
                MdcKeys.SESSION_ID,
                MdcKeys.CLIENT_IP,
                MdcKeys.HTTP_METHOD,
                MdcKeys.HTTP_PATH,
                MdcKeys.ROUTE_TEMPLATE,
                MdcKeys.USER_AGENT,
                MdcKeys.EVENT_CODE
        };
    }
}
