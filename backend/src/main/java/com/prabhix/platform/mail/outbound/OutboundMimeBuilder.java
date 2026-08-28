package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.mail.outbound.transport.MailTransport;
import com.prabhix.platform.mail.provisioning.DkimSigningService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboundMimeBuilder {

    private final JavaMailSender mailSender;
    private final DkimSigningService dkimSigningService;

    public MimeMessage build(MailTransport.OutboundMail mail) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(mail.getFromAddress(), mail.getFromName()));
        helper.setTo(mail.getToAddresses().toArray(String[]::new));
        if (mail.getCcAddresses() != null && !mail.getCcAddresses().isEmpty()) {
            helper.setCc(mail.getCcAddresses().toArray(String[]::new));
        }
        if (mail.getBccAddresses() != null && !mail.getBccAddresses().isEmpty()) {
            helper.setBcc(mail.getBccAddresses().toArray(String[]::new));
        }
        helper.setSubject(mail.getSubject());
        helper.setText(mail.getBodyText() != null ? mail.getBodyText() : "", mail.getBodyHtml());
        if (mail.getReplyTo() != null) {
            helper.setReplyTo(mail.getReplyTo());
        }
        if (mail.getHeaders() != null) {
            for (var entry : mail.getHeaders().entrySet()) {
                message.setHeader(entry.getKey(), entry.getValue());
            }
        }
        if (mail.getAttachments() != null) {
            for (MailTransport.AttachmentPart attachment : mail.getAttachments()) {
                helper.addAttachment(
                        attachment.getFilename(),
                        () -> new java.io.ByteArrayInputStream(attachment.getContent()),
                        attachment.getContentType());
            }
        }
        message.saveChanges();
        dkimSigningService.signIfApplicable(message, mail.getOrganizationId(), mail.getFromAddress());
        return message;
    }
}
