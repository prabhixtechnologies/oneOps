package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.mail.outbound.OutboundMimeBuilder;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SelfHostedSmtpTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final OutboundMimeBuilder mimeBuilder;

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

    @Override
    public boolean healthy() {
        return true;
    }
}
