package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.outbound.OutboundMimeBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.RawMessage;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.io.ByteArrayOutputStream;

/**
 * Sends through Amazon SES as raw MIME rather than the simple content API, because the
 * threading headers ({@code Message-ID}, {@code References}, {@code In-Reply-To}) and our own
 * DKIM signature have to survive intact for replies to land back on the right thread.
 */
@Slf4j
@Component
public class SesTransport implements MailTransport {

    private final PrabhixProperties properties;
    private final OutboundMimeBuilder mimeBuilder;
    private volatile SesV2Client client;

    public SesTransport(PrabhixProperties properties, OutboundMimeBuilder mimeBuilder) {
        this.properties = properties;
        this.mimeBuilder = mimeBuilder;
    }

    @Override
    public MailTransportResult send(OutboundMail mail) {
        try {
            MimeMessage message = mimeBuilder.build(mail);
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            message.writeTo(raw);

            SendEmailRequest.Builder request = SendEmailRequest.builder()
                    .content(EmailContent.builder()
                            .raw(RawMessage.builder()
                                    .data(SdkBytes.fromByteArray(raw.toByteArray()))
                                    .build())
                            .build());

            String configurationSet = properties.mail().ses().configurationSet();
            if (configurationSet != null && !configurationSet.isBlank()) {
                request.configurationSetName(configurationSet);
            }

            SendEmailResponse response = client().sendEmail(request.build());
            return MailTransportResult.ok(response.messageId());
        } catch (SesV2Exception ex) {
            // The SDK message alone omits the reason, which is what makes a bounce diagnosable.
            String detail = ex.awsErrorDetails() == null
                    ? ex.getMessage()
                    : ex.awsErrorDetails().errorCode() + ": " + ex.awsErrorDetails().errorMessage();
            log.warn("SES rejected message to {}: {}", mail.getToAddresses(), detail);
            return MailTransportResult.fail(detail);
        } catch (Exception ex) {
            log.warn("SES send failed for {}: {}", mail.getToAddresses(), ex.getMessage());
            return MailTransportResult.fail(ex.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "SES";
    }

    @Override
    public boolean healthy() {
        // SES resolves credentials from the instance role when no keys are set, so the only
        // configuration that cannot work is a missing region.
        String region = properties.mail().ses().region();
        return region != null && !region.isBlank();
    }

    private SesV2Client client() {
        SesV2Client existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                PrabhixProperties.Mail.Ses ses = properties.mail().ses();
                var builder = SesV2Client.builder().region(Region.of(ses.region()));
                if (ses.hasStaticCredentials()) {
                    builder.credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(ses.accessKey(), ses.secretKey())));
                } else {
                    builder.credentialsProvider(DefaultCredentialsProvider.create());
                }
                client = builder.build();
                log.info("SES transport ready in region {}{}", ses.region(),
                        ses.configurationSet().isBlank() ? "" : " (configuration set "
                                + ses.configurationSet() + ")");
            }
            return client;
        }
    }

    @PreDestroy
    void close() {
        SesV2Client existing = client;
        if (existing != null) {
            existing.close();
        }
    }
}
