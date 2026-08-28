package com.prabhix.platform.mail.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailWebhookEvent;
import com.prabhix.platform.mail.repository.MailWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesWebhookServiceTest {

    @Mock private MailWebhookEventRepository webhookEventRepository;
    @Mock private SesFeedbackService sesFeedbackService;
    @Mock private SnsMessageVerifier snsMessageVerifier;

    private SesWebhookService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new SesWebhookService(webhookEventRepository, sesFeedbackService, snsMessageVerifier, objectMapper);
    }

    @Test
    void rejectsInvalidSignature() {
        byte[] body = "{\"Type\":\"Notification\"}".getBytes(StandardCharsets.UTF_8);
        when(snsMessageVerifier.verify(any(), any())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.receive(body));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void duplicateSnsMessageIsNoOp() throws Exception {
        String body = """
                {
                  "Type": "Notification",
                  "MessageId": "sns-dup",
                  "TopicArn": "arn:aws:sns:us-east-1:123:topic",
                  "Message": "{\\"notificationType\\":\\"Bounce\\"}",
                  "Timestamp": "2026-08-28T00:00:00.000Z",
                  "Signature": "sig",
                  "SigningCertURL": "https://sns.us-east-1.amazonaws.com/cert.pem",
                  "SignatureVersion": "1"
                }
                """;
        when(snsMessageVerifier.verify(any(), any())).thenReturn(true);
        when(webhookEventRepository.findByProviderAndProviderEventId(
                MailEnums.WebhookProvider.SES_SNS, "sns-dup"))
                .thenReturn(Optional.of(new MailWebhookEvent()));

        service.receive(body.getBytes(StandardCharsets.UTF_8));

        verify(webhookEventRepository, never()).save(any());
    }
}
