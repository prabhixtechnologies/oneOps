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

    /*
     * There used to be a case here asserting that a transport with no static access keys is healthy,
     * from when healthy() only checked that a region was set. Once it became a live probe the same
     * assertion turned into a network call: it built a real SesV2Client, resolved whatever credentials
     * the machine happened to have, and passed on a developer laptop with an ~/.aws/credentials file
     * while failing on CI, which has none. It was also asserting the opposite of current behaviour —
     * no resolvable credentials now means unusable, deliberately, since that is an EC2 instance with
     * no role attached and every send from it is rejected.
     *
     * SesTransportHealthTest covers the real intent against a mocked client: healthyWhenReadsAreDenied
     * for a least-privilege send-only role, and unhealthyWithoutCredentials for the failure this one
     * was accidentally hiding.
     */

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
