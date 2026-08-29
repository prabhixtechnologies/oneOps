package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.outbound.OutboundMimeBuilder;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest;
import software.amazon.awssdk.services.sesv2.model.GetAccountResponse;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityRequest;
import software.amazon.awssdk.services.sesv2.model.GetEmailIdentityResponse;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code healthy()} used to check only that a region was set, which is the same shape of answer that
 * let the SMTP transport claim it could deliver to a port with nothing listening. These cases are
 * the states production is actually in or passes through on the way to sending real mail.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SesTransportHealthTest {

    private static final String FROM = "no-reply@prabhix.test";
    private static final String DOMAIN = "prabhix.test";

    @Mock
    SesV2Client client;
    @Mock
    OutboundMimeBuilder mimeBuilder;

    private SesTransport transport(String region) {
        PrabhixProperties properties = TestProperties.withMail(
                TestProperties.mail("SES", TestProperties.threading(),
                        TestProperties.ses(region, "")));
        return new SesTransport(properties, mimeBuilder, client);
    }

    private void accountCanSend(boolean productionAccess) {
        when(client.getAccount(any(GetAccountRequest.class))).thenReturn(GetAccountResponse.builder()
                .sendingEnabled(true)
                .productionAccessEnabled(productionAccess)
                .build());
    }

    private void identityVerified(String identity, boolean verified) {
        when(client.getEmailIdentity(GetEmailIdentityRequest.builder()
                .emailIdentity(identity).build()))
                .thenReturn(GetEmailIdentityResponse.builder()
                        .verifiedForSendingStatus(verified)
                        .build());
    }

    private void identityMissing(String identity) {
        when(client.getEmailIdentity(GetEmailIdentityRequest.builder()
                .emailIdentity(identity).build()))
                .thenThrow(NotFoundException.builder().message("not found").build());
    }

    private static SesV2Exception accessDenied(String action) {
        return (SesV2Exception) SesV2Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("AccessDeniedException")
                        .errorMessage("not authorized to perform: " + action)
                        .build())
                .message("access denied")
                .build();
    }

    @Test
    @DisplayName("healthy when the account may send and the from-domain is verified")
    void healthyWhenVerified() {
        accountCanSend(true);
        identityVerified(DOMAIN, true);
        SesTransport transport = transport("ap-south-1");

        assertThat(transport.healthy()).isTrue();
        assertThat(transport.healthNote()).isNull();
    }

    @Test
    @DisplayName("unhealthy when no region is configured, without calling SES")
    void unhealthyWithoutRegion() {
        SesTransport transport = transport("");

        assertThat(transport.healthy()).isFalse();
        assertThat(transport.healthNote()).contains("no SES region");
        verify(client, times(0)).getAccount(any(GetAccountRequest.class));
    }

    @Test
    @DisplayName("unhealthy when the domain is not an identity in this account")
    void unhealthyWhenDomainUnknown() {
        accountCanSend(true);
        identityMissing(DOMAIN);
        identityMissing(FROM);
        SesTransport transport = transport("ap-south-1");

        // The state a fresh SES account is in. Every send is rejected, so reporting healthy would
        // mean the outbox retried six times and gave up with nothing said about the cause.
        assertThat(transport.healthy()).isFalse();
        assertThat(transport.healthNote()).contains(DOMAIN).contains("is an SES identity");
    }

    @Test
    @DisplayName("unhealthy while domain verification is still pending")
    void unhealthyWhileVerificationPending() {
        accountCanSend(true);
        identityVerified(DOMAIN, false);
        identityMissing(FROM);
        SesTransport transport = transport("ap-south-1");

        assertThat(transport.healthy()).isFalse();
        assertThat(transport.healthNote()).contains("DKIM CNAME");
    }

    @Test
    @DisplayName("unhealthy when the account is not allowed to send at all")
    void unhealthyWhenSendingDisabled() {
        when(client.getAccount(any(GetAccountRequest.class))).thenReturn(GetAccountResponse.builder()
                .sendingEnabled(false)
                .productionAccessEnabled(true)
                .build());
        SesTransport transport = transport("ap-south-1");

        assertThat(transport.healthy()).isFalse();
        assertThat(transport.healthNote()).contains("sending is disabled");
    }

    @Test
    @DisplayName("unhealthy when credentials cannot be resolved")
    void unhealthyWithoutCredentials() {
        when(client.getAccount(any(GetAccountRequest.class)))
                .thenThrow(SdkClientException.create("Unable to load credentials"));
        SesTransport transport = transport("ap-south-1");

        // What an EC2 instance with no role attached does. Previously indistinguishable from a
        // working setup, because a region was configured either way.
        assertThat(transport.healthy()).isFalse();
        assertThat(transport.healthNote()).contains("no credentials");
    }

    @Test
    @DisplayName("healthy but flagged when the account is still in the sandbox")
    void healthyButSandboxed() {
        accountCanSend(false);
        identityVerified(DOMAIN, true);
        SesTransport transport = transport("ap-south-1");

        // Sends to verified recipients succeed, so this is not a reason to refuse. It is a reason to
        // say something: to everyone else, a sandboxed account looks like mail that never arrives.
        assertThat(transport.healthy()).isTrue();
        assertThat(transport.healthNote()).contains("sandbox");
    }

    @Test
    @DisplayName("healthy but flagged when sending as a verified address rather than a domain")
    void healthyWithAddressIdentityOnly() {
        accountCanSend(true);
        identityMissing(DOMAIN);
        identityVerified(FROM, true);
        SesTransport transport = transport("ap-south-1");

        assertThat(transport.healthy()).isTrue();
        assertThat(transport.healthNote()).contains("verified address");
    }

    @Test
    @DisplayName("a denied read does not make a send-only role look broken")
    void healthyWhenReadsAreDenied() {
        when(client.getAccount(any(GetAccountRequest.class)))
                .thenThrow(accessDenied("ses:GetAccount"));
        when(client.getEmailIdentity(any(GetEmailIdentityRequest.class)))
                .thenThrow(accessDenied("ses:GetEmailIdentity"));
        SesTransport transport = transport("ap-south-1");

        // A policy granting only ses:SendEmail is correct least privilege. Being denied the read
        // says nothing about whether a send would succeed, and treating it as unhealthy would take
        // the transport down for being configured properly.
        assertThat(transport.healthy()).isTrue();
        assertThat(transport.healthNote()).contains("unconfirmed");
    }

    @Test
    @DisplayName("probes once per minute, not once per message")
    void cachesTheProbe() {
        accountCanSend(true);
        identityVerified(DOMAIN, true);
        SesTransport transport = transport("ap-south-1");

        for (int i = 0; i < 10; i++) {
            transport.healthy();
        }

        // MailTransportRouter.select() runs per message in the outbox worker, so an uncached probe
        // would be two SES API calls for every email sent.
        verify(client, times(1)).getAccount(any(GetAccountRequest.class));
    }
}
