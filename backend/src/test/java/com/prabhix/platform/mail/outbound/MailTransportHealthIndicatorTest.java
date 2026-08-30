package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.mail.outbound.transport.MailTransportRouter;
import com.prabhix.platform.mail.outbound.transport.SesTransport;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailTransportHealthIndicatorTest {

    @Mock
    MailTransportRouter transportRouter;
    @Mock
    SesTransport sesTransport;

    @Test
    void reportsUpWithTheTransportTheRouterWouldUse() {
        when(sesTransport.providerId()).thenReturn("SES");
        when(transportRouter.select()).thenReturn(sesTransport);

        Health health = indicator("SES").health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("SES", health.getDetails().get("selected"));
    }

    @Test
    void reportsUpWhenTheChainFellBackToAnotherTransport() {
        when(sesTransport.providerId()).thenReturn("SMTP_RELAY");
        when(transportRouter.select()).thenReturn(sesTransport);

        Health health = indicator("SES").health();

        // Mail is still leaving, so this is not a page. The mismatch between configured and
        // selected is the detail an operator needs to see that SES is the part that is broken.
        assertEquals(Status.UP, health.getStatus());
        assertEquals("SES", health.getDetails().get("configured"));
        assertEquals("SMTP_RELAY", health.getDetails().get("selected"));
    }

    @Test
    void reportsDownWhenNothingCanDeliver() {
        when(transportRouter.select())
                .thenThrow(new IllegalStateException("No mail transport can deliver"));

        Health health = indicator("SES").health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("No mail transport can deliver", health.getDetails().get("reason"));
    }

    private MailTransportHealthIndicator indicator(String transport) {
        return new MailTransportHealthIndicator(
                transportRouter, TestProperties.withMail(TestProperties.mail(transport)));
    }
}
