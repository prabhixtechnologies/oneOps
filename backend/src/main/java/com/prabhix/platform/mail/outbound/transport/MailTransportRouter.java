package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.util.LocalMailProfiles;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class MailTransportRouter {

    private static final int CIRCUIT_THRESHOLD = 5;

    private final PrabhixProperties properties;
    private final Environment environment;
    private final LoggingTransport loggingTransport;
    private final SelfHostedSmtpTransport selfHostedSmtpTransport;
    private final SmtpRelayTransport smtpRelayTransport;
    private final SesTransport sesTransport;
    private final Map<String, AtomicInteger> recentFailures = new ConcurrentHashMap<>();

    /**
     * The transport to send the next message with.
     *
     * @throws IllegalStateException outside dev/test when nothing can deliver, so the caller records
     *     a failure and retries later instead of the message being lost.
     */
    public MailTransport select() {
        String configured = properties.mail().transport();
        List<String> rejected = new ArrayList<>();
        for (MailTransport transport : chainFor(configured)) {
            if (available(transport)) {
                return transport;
            }
            rejected.add(transport.providerId() + " (" + why(transport) + ")");
        }
        // Logging is a destination only where nobody expects mail to arrive. Everywhere else,
        // refusing to send is the honest outcome: MailTransportResult.ok() from LoggingTransport is
        // indistinguishable from a real send, so OutboxWorker marked the row SENT and the message
        // was gone. Production ran this way — outbox rows read SENT with transport_used=LOGGING and
        // a connection-refused error attached, because the chain ended in loggingTransport and the
        // final fallback returned it even when the chain was exhausted.
        if (LocalMailProfiles.isLocal(environment)) {
            return loggingTransport;
        }
        // The reasons are carried in the message rather than only logged: this string reaches the
        // outbox row's last_error and /actuator/health, which are the two places anyone looks when
        // mail stops arriving.
        throw new IllegalStateException("No mail transport can deliver: MAIL_TRANSPORT=" + configured
                + (rejected.isEmpty()
                ? " names no transport" : ", and " + String.join(", ", rejected)));
    }

    private String why(MailTransport transport) {
        if (circuitOpen(transport.providerId())) {
            return "circuit open after " + CIRCUIT_THRESHOLD + " consecutive failures";
        }
        String note = transport.healthNote();
        return note == null ? "reported unhealthy" : note;
    }

    /**
     * Candidates in preference order. Deliberately without a logging tail — see {@link #select()}.
     */
    private List<MailTransport> chainFor(String configured) {
        return switch (configured) {
            case "SELF_HOSTED_SMTP" -> List.of(selfHostedSmtpTransport);
            case "SMTP_RELAY" -> List.of(smtpRelayTransport);
            case "SES" -> List.of(sesTransport, smtpRelayTransport);
            default -> List.of();
        };
    }

    /** Breaker first: it is an in-memory counter, whereas {@code healthy()} may probe the network. */
    private boolean available(MailTransport transport) {
        return !circuitOpen(transport.providerId()) && transport.healthy();
    }

    private boolean circuitOpen(String providerId) {
        AtomicInteger failures = recentFailures.get(providerId);
        return failures != null && failures.get() >= CIRCUIT_THRESHOLD;
    }

    public void recordFailure(String providerId) {
        recentFailures.computeIfAbsent(providerId, k -> new AtomicInteger()).incrementAndGet();
    }

    public void recordSuccess(String providerId) {
        recentFailures.computeIfAbsent(providerId, k -> new AtomicInteger()).set(0);
    }
}
