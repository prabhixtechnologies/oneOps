package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.mail.outbound.OutboundMimeBuilder;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelfHostedSmtpTransport implements MailTransport {

    /**
     * Short enough that the outbox worker never blocks for long on an unreachable host, and well
     * under {@code spring.mail.properties.mail.smtp.connectiontimeout}, whose 10s is what a send
     * would otherwise cost per message.
     */
    private static final int PROBE_TIMEOUT_MS = 3_000;

    /**
     * {@link MailTransportRouter#select()} runs once per message, so an uncached probe would open a
     * socket per message. Postfix logs an aborted probe as "lost connection after CONNECT", so this
     * also keeps that noise to a trickle.
     */
    private static final Duration PROBE_TTL = Duration.ofSeconds(15);

    private final JavaMailSender mailSender;
    private final OutboundMimeBuilder mimeBuilder;
    private final MailProperties mailProperties;

    private volatile Instant probedAt = Instant.EPOCH;
    private volatile boolean reachable;
    private volatile String note;

    @Override
    public MailTransportResult send(OutboundMail mail) {
        try {
            MimeMessage message = mimeBuilder.build(mail);
            mailSender.send(message);
            return MailTransportResult.ok(message.getMessageID());
        } catch (Exception ex) {
            return MailTransportResult.fail(ex.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "SELF_HOSTED_SMTP";
    }

    /**
     * Whether the configured SMTP host is accepting connections.
     *
     * <p>This used to return an unconditional {@code true}, which is how production came to be
     * pointed at {@code localhost:587} with nothing listening while still reporting a usable
     * transport. The router took it at its word, the send failed, and the message fell through to
     * the logging transport and was recorded as sent.
     *
     * <p>A TCP connect, not an SMTP handshake: it distinguishes "nothing is listening" — the failure
     * that actually happened — without needing credentials or a deliverable envelope.
     */
    @Override
    public boolean healthy() {
        Instant now = Instant.now();
        if (Duration.between(probedAt, now).compareTo(PROBE_TTL) < 0) {
            return reachable;
        }
        reachable = canConnect();
        probedAt = now;
        return reachable;
    }

    @Override
    public String healthNote() {
        return note;
    }

    private boolean canConnect() {
        String host = mailProperties.getHost();
        Integer port = mailProperties.getPort();
        if (host == null || host.isBlank() || port == null) {
            note = "no SMTP host or port is configured";
            log.warn("SMTP transport has no host or port configured; treating it as unavailable");
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            note = null;
            return true;
        } catch (IOException ex) {
            // Names the host and port, because the failure in production was SMTP_HOST=localhost
            // with no mail server running there — which the message has to say to be actionable.
            note = host + ":" + port + " is unreachable (" + ex.getMessage() + ")";
            log.warn("SMTP host {}:{} is unreachable ({}); outbound mail will be retried, not sent",
                    host, port, ex.getMessage());
            return false;
        }
    }
}
