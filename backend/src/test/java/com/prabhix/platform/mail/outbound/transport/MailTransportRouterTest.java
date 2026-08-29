package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The router used to end every chain in the logging transport and return it again as a final
 * fallback. Logging reports success, so an undeliverable message was recorded {@code SENT} with
 * {@code transport_used = LOGGING} and quietly dropped. Production ran that way.
 */
@ExtendWith(MockitoExtension.class)
class MailTransportRouterTest {

    @Mock
    LoggingTransport loggingTransport;
    @Mock
    SelfHostedSmtpTransport selfHostedSmtpTransport;
    @Mock
    SmtpRelayTransport smtpRelayTransport;
    @Mock
    SesTransport sesTransport;

    @Test
    void refusesToSendWhenTheOnlyTransportIsUnreachableInProduction() {
        when(smtpRelayTransport.providerId()).thenReturn("SMTP_RELAY");
        when(smtpRelayTransport.healthy()).thenReturn(false);
        MailTransportRouter router = router("SMTP_RELAY", "prod");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, router::select);

        // The message is what an operator reads at 2am, so it names the setting to change.
        assertTrue(thrown.getMessage().contains("MAIL_TRANSPORT=SMTP_RELAY"));
        verifyNoInteractions(loggingTransport);
    }

    @Test
    void refusesToSendEvenWhenLoggingIsTheConfiguredTransportInProduction() {
        MailTransportRouter router = router("LOGGING", "prod");

        // MailTransportStartupValidator should have refused to boot already. This is the second
        // line of defence, because the cost of getting it wrong is silently destroyed mail.
        assertThrows(IllegalStateException.class, router::select);
        verifyNoInteractions(loggingTransport);
    }

    @Test
    void logsInsteadOfSendingWhenNoProfileIsActive() {
        when(smtpRelayTransport.providerId()).thenReturn("SMTP_RELAY");
        when(smtpRelayTransport.healthy()).thenReturn(false);
        MailTransportRouter router = router("SMTP_RELAY");

        // A developer with no mail server running still gets a working sign-up flow.
        assertSame(loggingTransport, router.select());
    }

    @Test
    void logsInsteadOfSendingUnderTheDevProfile() {
        MailTransportRouter router = router("LOGGING", "dev");

        assertSame(loggingTransport, router.select());
    }

    @Test
    void prefersSesWhenItIsConfiguredAndHealthy() {
        when(sesTransport.providerId()).thenReturn("SES");
        when(sesTransport.healthy()).thenReturn(true);
        MailTransportRouter router = router("SES", "prod");

        assertSame(sesTransport, router.select());
    }

    @Test
    void fallsBackFromSesToTheRelay() {
        when(sesTransport.providerId()).thenReturn("SES");
        when(sesTransport.healthy()).thenReturn(false);
        when(smtpRelayTransport.providerId()).thenReturn("SMTP_RELAY");
        when(smtpRelayTransport.healthy()).thenReturn(true);
        MailTransportRouter router = router("SES", "prod");

        assertSame(smtpRelayTransport, router.select());
    }

    @Test
    void stopsProbingATransportOnceItsCircuitOpens() {
        when(sesTransport.providerId()).thenReturn("SES");
        when(smtpRelayTransport.providerId()).thenReturn("SMTP_RELAY");
        when(smtpRelayTransport.healthy()).thenReturn(true);
        MailTransportRouter router = router("SES", "prod");
        for (int i = 0; i < 5; i++) {
            router.recordFailure("SES");
        }

        assertSame(smtpRelayTransport, router.select());
        // An open circuit short-circuits before healthy(), which for the SMTP transports opens a
        // socket. Probing a transport we have already decided not to use is wasted latency per
        // message in the outbox worker.
        verify(sesTransport, never()).healthy();
    }

    @Test
    void closesTheCircuitAgainAfterASuccess() {
        when(sesTransport.providerId()).thenReturn("SES");
        when(sesTransport.healthy()).thenReturn(true);
        MailTransportRouter router = router("SES", "prod");
        for (int i = 0; i < 5; i++) {
            router.recordFailure("SES");
        }
        router.recordSuccess("SES");

        assertSame(sesTransport, router.select());
    }

    @Test
    void namesWhyEachTransportWasRejected() {
        when(sesTransport.providerId()).thenReturn("SES");
        when(sesTransport.healthy()).thenReturn(false);
        when(sesTransport.healthNote()).thenReturn("prabhix.test is not an SES identity");
        when(smtpRelayTransport.providerId()).thenReturn("SMTP_RELAY");
        when(smtpRelayTransport.healthy()).thenReturn(false);
        when(smtpRelayTransport.healthNote()).thenReturn("localhost:587 is unreachable");
        MailTransportRouter router = router("SES", "prod");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, router::select);

        // This message becomes the outbox row's last_error and the health endpoint's reason, so it
        // is the whole diagnosis anyone gets. "Unhealthy" alone would send them reading logs.
        assertTrue(thrown.getMessage().contains("SES (prabhix.test is not an SES identity)"));
        assertTrue(thrown.getMessage().contains("SMTP_RELAY (localhost:587 is unreachable)"));
    }

    @Test
    void reportsAnOpenCircuitRatherThanTheStaleHealthNote() {
        when(sesTransport.providerId()).thenReturn("SES");
        MailTransportRouter router = router("SES", "prod");
        for (int i = 0; i < 5; i++) {
            router.recordFailure("SES");
        }
        when(smtpRelayTransport.providerId()).thenReturn("SMTP_RELAY");
        when(smtpRelayTransport.healthy()).thenReturn(false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, router::select);

        // A breaker-tripped transport may still report itself healthy, which would otherwise read as
        // "SES (null)" and hide the actual reason it is not being used.
        assertTrue(thrown.getMessage().contains("SES (circuit open"));
    }

    @Test
    void refusesToSendWhenEveryTransportInTheChainIsUnreachable() {
        when(sesTransport.providerId()).thenReturn("SES");
        when(sesTransport.healthy()).thenReturn(false);
        when(smtpRelayTransport.providerId()).thenReturn("SMTP_RELAY");
        when(smtpRelayTransport.healthy()).thenReturn(false);
        MailTransportRouter router = router("SES", "prod");

        assertThrows(IllegalStateException.class, router::select);
        verifyNoInteractions(loggingTransport);
    }

    /** No profiles models a bare local run, which is where logging is still the right answer. */
    private MailTransportRouter router(String transport, String... profiles) {
        PrabhixProperties properties = TestProperties.withMail(TestProperties.mail(transport));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new MailTransportRouter(properties, environment, loggingTransport,
                selfHostedSmtpTransport, smtpRelayTransport, sesTransport);
    }
}
