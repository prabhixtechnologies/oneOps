package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class MailTransportRouter {

    private static final int CIRCUIT_THRESHOLD = 5;

    private final PrabhixProperties properties;
    private final LoggingTransport loggingTransport;
    private final SelfHostedSmtpTransport selfHostedSmtpTransport;
    private final SmtpRelayTransport smtpRelayTransport;
    private final SesTransport sesTransport;
    private final Map<String, AtomicInteger> recentFailures = new ConcurrentHashMap<>();

    public MailTransport select() {
        String configured = properties.mail().transport();
        List<MailTransport> chain = switch (configured) {
            case "SELF_HOSTED_SMTP" -> List.of(selfHostedSmtpTransport, loggingTransport);
            case "SMTP_RELAY" -> List.of(smtpRelayTransport, loggingTransport);
            case "SES" -> List.of(sesTransport, smtpRelayTransport, loggingTransport);
            default -> List.of(loggingTransport);
        };
        for (MailTransport transport : chain) {
            if (transport.healthy() && recentFailures.getOrDefault(transport.providerId(), new AtomicInteger()).get() < CIRCUIT_THRESHOLD) {
                return transport;
            }
        }
        return loggingTransport;
    }

    public void recordFailure(String providerId) {
        recentFailures.computeIfAbsent(providerId, k -> new AtomicInteger()).incrementAndGet();
    }

    public void recordSuccess(String providerId) {
        recentFailures.computeIfAbsent(providerId, k -> new AtomicInteger()).set(0);
    }
}
