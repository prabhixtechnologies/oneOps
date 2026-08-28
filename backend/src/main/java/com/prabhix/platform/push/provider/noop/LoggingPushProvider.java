package com.prabhix.platform.push.provider.noop;

import com.prabhix.platform.observability.redaction.LogRedactor;
import com.prabhix.platform.push.provider.PushProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class LoggingPushProvider implements PushProvider {

    @Override
    public String providerId() {
        return "LOGGING";
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public SendResult send(SendRequest request) {
        Map<String, Object> safe = LogRedactor.redactMap(Map.of(
                "type", request.notificationType(),
                "organizationId", request.organizationId(),
                "userId", request.userId(),
                "platform", request.platform().name(),
                "deviceToken", LogRedactor.REDACTED,
                "payload", request.payload()));
        log.debug("Push not configured; would send notification: {}", safe);
        return SendResult.ok("logging-noop");
    }
}
