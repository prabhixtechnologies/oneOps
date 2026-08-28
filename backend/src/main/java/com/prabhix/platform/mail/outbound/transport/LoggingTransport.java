package com.prabhix.platform.mail.outbound.transport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingTransport implements MailTransport {

    @Override
    public MailTransportResult send(OutboundMail mail) {
        log.info("MAIL [{}] -> {} subject={}", providerId(),
                mail.getToAddresses(), mail.getSubject());
        return MailTransportResult.ok("log-" + UUID.randomUUID());
    }

    @Override
    public String providerId() {
        return "LOGGING";
    }

    @Override
    public boolean healthy() {
        return true;
    }
}
