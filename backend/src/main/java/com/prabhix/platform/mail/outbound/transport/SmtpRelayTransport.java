package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class SmtpRelayTransport implements MailTransport {

    private final SelfHostedSmtpTransport delegate;

    public SmtpRelayTransport(SelfHostedSmtpTransport delegate) {
        this.delegate = delegate;
    }

    @Override
    public MailTransportResult send(OutboundMail mail) {
        return delegate.send(mail);
    }

    @Override
    public String providerId() {
        return "SMTP_RELAY";
    }

    @Override
    public boolean healthy() {
        return delegate.healthy();
    }
}
