package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.outbound.OutboundMimeBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest;
import software.amazon.awssdk.services.sesv2.model.GetAccountResponse;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.RawMessage;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;

/**
 * Sends through Amazon SES as raw MIME rather than the simple content API, because the
 * threading headers ({@code Message-ID}, {@code References}, {@code In-Reply-To}) and our own
 * DKIM signature have to survive intact for replies to land back on the right thread.
 */
@Slf4j
@Component
public class SesTransport implements MailTransport {

    /**
     * {@link MailTransportRouter#select()} runs once per message and the probe makes two network
     * calls, so it is cached. A minute: verification and sandbox status change on the order of days,
     * and a newly granted permission or a just-verified domain still takes effect without a restart.
     */
    private static final Duration PROBE_TTL = Duration.ofMinutes(1);

    private final PrabhixProperties properties;
    private final OutboundMimeBuilder mimeBuilder;
    private volatile SesV2Client client;
    private volatile Probe probe;

    /**
     * {@code @Autowired} because this class has two constructors, and with more than one Spring does
     * not choose: it looks for a no-argument constructor, finds none, and fails to start the whole
     * application with "No default constructor found" — which names this class but not the reason.
     * One constructor needs to say it is the one to inject.
     */
    @Autowired
    public SesTransport(PrabhixProperties properties, OutboundMimeBuilder mimeBuilder) {
        this.properties = properties;
        this.mimeBuilder = mimeBuilder;
    }

    /**
     * Supplies the client rather than building it from configuration, so the sendability probe can be
     * tested against SES's actual responses. Everything it distinguishes — an unverified domain, a
     * sandboxed account, a denied read on a send-only role — is a real production state, and getting
     * one of them backwards would report healthy for a configuration that delivers nothing.
     */
    SesTransport(PrabhixProperties properties, OutboundMimeBuilder mimeBuilder, SesV2Client client) {
        this(properties, mimeBuilder);
        this.client = client;
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
            String detail = describe(ex);
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

    /**
     * Whether SES will accept a send.
     *
     * <p>This used to check only that a region was configured, which is the same shape of answer
     * that let mail be discarded through the SMTP transport: everything that decides the outcome —
     * whether credentials resolve at all, whether the account may send, whether the from-address is
     * a verified identity — went unasked, so a wholly unusable configuration reported healthy.
     */
    @Override
    public boolean healthy() {
        return probe().usable();
    }

    @Override
    public String healthNote() {
        return probe().note();
    }

    private Probe probe() {
        Probe cached = probe;
        if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(PROBE_TTL) < 0) {
            return cached;
        }
        Probe fresh = runProbe();
        probe = fresh;
        if (!fresh.usable()) {
            log.warn("SES cannot send: {}", fresh.note());
        }
        return fresh;
    }

    private Probe runProbe() {
        String region = properties.mail().ses().region();
        if (region == null || region.isBlank()) {
            return Probe.unusable("no SES region is configured");
        }

        String accountNote;
        try {
            accountNote = accountNote();
        } catch (Unusable ex) {
            return Probe.unusable(ex.getMessage());
        } catch (SesV2Exception ex) {
            if (!isAccessDenied(ex)) {
                return Probe.unusable("SES rejected GetAccount: " + describe(ex));
            }
            // A send-only IAM policy is a legitimate configuration, and being denied the read says
            // nothing about whether a send would succeed. Reporting unhealthy here would take the
            // transport down for being correctly least-privileged.
            accountNote = "sending status unconfirmed (no ses:GetAccount permission)";
        } catch (SdkException ex) {
            // No credentials at all, or SES unreachable. Definitive: the SDK resolves the instance
            // role here, so this is the failure that "a region is set" used to hide.
            return Probe.unusable("SES is unreachable or has no credentials: " + ex.getMessage());
        }

        try {
            return Probe.usable(join(accountNote, senderNote()));
        } catch (Unusable ex) {
            return Probe.unusable(ex.getMessage());
        } catch (SesV2Exception ex) {
            if (!isAccessDenied(ex)) {
                return Probe.unusable("SES rejected GetEmailIdentity: " + describe(ex));
            }
            return Probe.usable(join(accountNote,
                    "sender identity unconfirmed (no ses:GetEmailIdentity permission)"));
        } catch (SdkException ex) {
            return Probe.unusable("SES is unreachable: " + ex.getMessage());
        }
    }

    /**
     * @return a caveat about the account worth reporting, or null
     * @throws Unusable when the account may not send at all
     */
    private String accountNote() {
        GetAccountResponse account = client().getAccount(GetAccountRequest.builder().build());
        if (Boolean.FALSE.equals(account.sendingEnabled())) {
            throw new Unusable("sending is disabled for this SES account"
                    + (account.enforcementStatus() == null
                    ? "" : " (enforcement status " + account.enforcementStatus() + ")"));
        }
        if (Boolean.FALSE.equals(account.productionAccessEnabled())) {
            // Not unhealthy: sends to verified recipients do succeed. Worth saying out loud, because
            // to anyone else it presents as mail that leaves and never arrives.
            return "account is in the SES sandbox, so only verified recipients receive mail";
        }
        return null;
    }

    /**
     * SES accepts a send only from an identity it has verified, which may be the whole domain or
     * just the one address. Both are checked: either is a valid way to be set up, and the absence of
     * a domain identity is not evidence that the address is unverified.
     *
     * @throws Unusable when neither identity can send
     */
    private String senderNote() {
        String from = properties.mail().fromAddress();
        int at = from == null ? -1 : from.indexOf('@');
        if (at < 0) {
            throw new Unusable("MAIL_FROM is not an email address: " + from);
        }
        String domain = from.substring(at + 1);

        Boolean domainVerified = verifiedForSending(domain);
        if (Boolean.TRUE.equals(domainVerified)) {
            return null;
        }
        Boolean addressVerified = verifiedForSending(from);
        if (Boolean.TRUE.equals(addressVerified)) {
            return "sending as a verified address rather than a verified domain, so replies to other "
                    + "addresses on " + domain + " will be rejected";
        }
        if (domainVerified == null && addressVerified == null) {
            throw new Unusable("neither " + domain + " nor " + from
                    + " is an SES identity in this account, so every send will be rejected");
        }
        throw new Unusable("SES verification of " + domain
                + " is still pending; publish the DKIM CNAME records it asks for");
    }

    /** @return null when no such identity exists, otherwise whether it is verified for sending */
    private Boolean verifiedForSending(String identity) {
        try {
            return client().getEmailIdentity(GetEmailIdentityRequest.builder()
                            .emailIdentity(identity)
                            .build())
                    .verifiedForSendingStatus();
        } catch (NotFoundException ex) {
            return null;
        }
    }

    private static boolean isAccessDenied(SesV2Exception ex) {
        return ex.awsErrorDetails() != null
                && "AccessDeniedException".equals(ex.awsErrorDetails().errorCode());
    }

    private static String describe(SesV2Exception ex) {
        return ex.awsErrorDetails() == null
                ? ex.getMessage()
                : ex.awsErrorDetails().errorCode() + ": " + ex.awsErrorDetails().errorMessage();
    }

    private static String join(String first, String second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + "; " + second;
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

    /** A condition under which SES definitely will not deliver, as opposed to one we cannot check. */
    private static final class Unusable extends RuntimeException {
        Unusable(String message) {
            super(message);
        }
    }

    private record Probe(boolean usable, String note, Instant at) {
        static Probe usable(String note) {
            return new Probe(true, note, Instant.now());
        }

        static Probe unusable(String note) {
            return new Probe(false, note, Instant.now());
        }
    }
}
