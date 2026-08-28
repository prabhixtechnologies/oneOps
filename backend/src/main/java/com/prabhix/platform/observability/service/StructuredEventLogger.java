package com.prabhix.platform.observability.service;

import com.prabhix.platform.observability.context.CorrelationContext;
import com.prabhix.platform.observability.context.MdcKeys;
import com.prabhix.platform.observability.event.EventLogRequested;
import com.prabhix.platform.observability.redaction.LogRedactor;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * The only entry point for emitting taxonomy-defined operational events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredEventLogger {

    private final ApplicationEventPublisher events;
    private final ObservabilityMetrics metrics;

    public void log(LogEventCode code, Map<String, Object> payload) {
        log(code, payload, true);
    }

    public void log(LogEventCode code, Map<String, Object> payload, boolean persist) {
        Map<String, Object> safe = LogRedactor.redactMap(payload);
        MDC.put(MdcKeys.EVENT_CODE, code.code());

        switch (code.severity()) {
            case DEBUG -> log.debug("{} {}", code.code(), safe);
            case INFO -> log.info("{} {}", code.code(), safe);
            case WARN -> log.warn("{} {}", code.code(), safe);
            case ERROR, FATAL -> log.error("{} {}", code.code(), safe);
        }
        metrics.incrementEvent(code);

        if (persist) {
            events.publishEvent(buildEvent(code, safe));
        }
    }

    private EventLogRequested buildEvent(LogEventCode code, Map<String, Object> payload) {
        UUID orgId = TenantContext.current().orElse(null);
        Actor actor = resolveActor();

        return new EventLogRequested(
                orgId,
                code,
                code.category(),
                code.severity(),
                CorrelationContext.currentOrNew(),
                actor.userId(),
                actor.type(),
                actor.label(),
                null,
                null,
                payload,
                MDC.get(MdcKeys.CLIENT_IP),
                MDC.get(MdcKeys.USER_AGENT),
                code.securitySensitive(),
                code.carriesPii());
    }

    private Actor resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PrabhixPrincipal principal) {
            String type = principal.platformAdmin() ? "PLATFORM_ADMIN" : "USER";
            return new Actor(principal.userId(), type, principal.displayName());
        }
        return new Actor(null, "SYSTEM", "System");
    }

    private record Actor(UUID userId, String type, String label) {
    }
}
