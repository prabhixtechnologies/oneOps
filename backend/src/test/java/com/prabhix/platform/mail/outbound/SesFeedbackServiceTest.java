package com.prabhix.platform.mail.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesFeedbackServiceTest {

    @Mock private SuppressionService suppressionService;
    @Mock private MailOutboxRepository outboxRepository;

    private SesFeedbackService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new SesFeedbackService(suppressionService, outboxRepository, objectMapper);
    }

    @Test
    void recordsPermanentBounceForMatchingOutbox() throws Exception {
        UUID orgId = UUID.randomUUID();
        MailOutbox outbox = new MailOutbox();
        outbox.setOrganizationId(orgId);
        when(outboxRepository.findFirstByProviderMessageId("ses-msg-1")).thenReturn(Optional.of(outbox));

        String json = """
                {
                  "notificationType": "Bounce",
                  "bounce": {
                    "bounceType": "Permanent",
                    "bounceSubType": "General",
                    "bouncedRecipients": [{"emailAddress": "bad@example.com"}]
                  },
                  "mail": {"messageId": "ses-msg-1"}
                }
                """;
        service.processSesNotification(objectMapper.readTree(json));

        verify(suppressionService).recordHardBounce("bad@example.com", orgId, "General");
    }

    @Test
    void resolvesOrganizationFromMailTags() throws Exception {
        UUID orgId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        String json = """
                {
                  "notificationType": "Complaint",
                  "complaint": {
                    "complainedRecipients": [{"emailAddress": "spam@example.com"}]
                  },
                  "mail": {
                    "tags": {"organizationId": ["aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]}
                  }
                }
                """;
        UUID resolved = service.resolveOrganizationId(objectMapper.readTree(json));
        assertEquals(orgId, resolved);
    }
}
