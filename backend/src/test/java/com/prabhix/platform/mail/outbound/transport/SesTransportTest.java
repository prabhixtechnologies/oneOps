package com.prabhix.platform.mail.outbound.transport;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.outbound.OutboundMimeBuilder;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesTransportTest {

    @Mock
    OutboundMimeBuilder mimeBuilder;

    private SesTransport transport(String region, String configurationSet) {
        PrabhixProperties props = TestProperties.withMail(TestProperties.mail(
                "SES", TestProperties.threading(), TestProperties.ses(region, configurationSet)));
        return new SesTransport(props, mimeBuilder);
    }

    @Test
    void isHealthyWithoutStaticKeysBecauseTheInstanceRoleSuppliesThem() {
        assertTrue(transport("ap-south-1", "").healthy(),
                "SES on EC2 has no access keys; requiring them would disable the transport");
    }

    @Test
    void isUnhealthyWithoutARegion() {
        assertFalse(transport("", "").healthy());
    }

    @Test
    void reportsFailureRatherThanThrowingWhenMimeBuildingFails() throws Exception {
        when(mimeBuilder.build(any())).thenThrow(new IllegalStateException("bad address"));

        MailTransport.OutboundMail mail = new MailTransport.OutboundMail();
        mail.setToAddresses(List.of("someone@example.com"));

        MailTransport.MailTransportResult result = transport("ap-south-1", "").send(mail);

        // The outbox worker relies on a returned failure to schedule a retry; a thrown exception
        // would abort the whole batch instead.
        assertFalse(result.success());
        assertNotNull(result.error());
    }

    @Test
    void identifiesItselfAsSesSoTheRouterCanTrackItsCircuit() {
        assertTrue("SES".equals(transport("ap-south-1", "").providerId()));
    }
}
