package com.prabhix.platform.mail.outbound.transport;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MailTransport {

    MailTransportResult send(OutboundMail mail);

    String providerId();

    boolean healthy();

    @Getter
    @Setter
    class OutboundMail {
        private UUID organizationId;
        private String fromAddress;
        private String fromName;
        private String replyTo;
        private List<String> toAddresses;
        private List<String> ccAddresses;
        private List<String> bccAddresses;
        private String subject;
        private String bodyHtml;
        private String bodyText;
        private Map<String, String> headers;
        private List<AttachmentPart> attachments = List.of();
    }

    @Getter
    @Setter
    class AttachmentPart {
        private String filename;
        private String contentType;
        private byte[] content;
    }

    record MailTransportResult(boolean success, String providerMessageId, String error) {
        public static MailTransportResult ok(String id) {
            return new MailTransportResult(true, id, null);
        }

        public static MailTransportResult fail(String error) {
            return new MailTransportResult(false, null, error);
        }
    }
}
