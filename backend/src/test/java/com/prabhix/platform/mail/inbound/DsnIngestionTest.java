package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.MailSuppression;
import com.prabhix.platform.mail.outbound.SuppressionService;
import com.prabhix.platform.mail.repository.MailDeliveryEventRepository;
import com.prabhix.platform.mail.repository.MailSuppressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DsnIngestionTest {

    @Mock MailSuppressionRepository suppressionRepository;
    @Mock MailDeliveryEventRepository deliveryEventRepository;
    @Mock ApplicationEventPublisher events;

    SuppressionService suppressionService;
    MimeParser mimeParser = new MimeParser();

    @BeforeEach
    void setUp() {
        suppressionService = new SuppressionService(suppressionRepository, deliveryEventRepository, events);
    }

    @Test
    void hardBounce5511RecordsSuppression() throws Exception {
        String dsn = hardBounceDsn("bounced@recipient.com", "5.1.1");
        var parsed = mimeParser.parse(dsn.getBytes(StandardCharsets.UTF_8));
        assertTrue(DsnDetector.isDeliveryStatusNotification(
                new jakarta.mail.internet.MimeMessage(
                        jakarta.mail.Session.getInstance(new java.util.Properties()),
                        new java.io.ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8))),
                parsed));

        when(suppressionRepository.findActive(any(), any(), any())).thenReturn(Optional.empty());
        when(suppressionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        suppressionService.parseAndRecordDsn(extractBody(dsn), UUID.randomUUID());

        ArgumentCaptor<MailSuppression> captor = ArgumentCaptor.forClass(MailSuppression.class);
        verify(suppressionRepository).save(captor.capture());
        assertEquals(MailEnums.SuppressionReason.HARD_BOUNCE, captor.getValue().getReason());
        assertEquals("bounced@recipient.com", captor.getValue().getAddress());
    }

    @Test
    void softBounce4222RecordsDeliveryEventWithoutHardSuppression() throws Exception {
        String dsn = hardBounceDsn("full@recipient.com", "4.2.2");
        when(suppressionRepository.findActive(any(), any(), any())).thenReturn(Optional.empty());
        when(suppressionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        suppressionService.parseAndRecordDsn(extractBody(dsn), UUID.randomUUID());

        verify(deliveryEventRepository).save(any());
        ArgumentCaptor<MailSuppression> captor = ArgumentCaptor.forClass(MailSuppression.class);
        verify(suppressionRepository).save(captor.capture());
        assertEquals(MailEnums.SuppressionReason.SOFT_BOUNCE, captor.getValue().getReason());
    }

    private String extractBody(String dsn) throws Exception {
        var message = new jakarta.mail.internet.MimeMessage(
                jakarta.mail.Session.getInstance(new java.util.Properties()),
                new java.io.ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)));
        return DsnDetector.extractReportBody(message);
    }

    private static String hardBounceDsn(String recipient, String status) {
        return """
                From: MAILER-DAEMON@mail.example.com
                To: support@customer.com
                Subject: Undelivered Mail Returned to Sender
                MIME-Version: 1.0
                Content-Type: multipart/report; report-type=delivery-status; boundary=bound

                --bound
                Content-Type: text/plain; charset=UTF-8

                Delivery failed.
                --bound
                Content-Type: message/delivery-status

                Reporting-MTA: dns; mail.example.com
                Final-Recipient: rfc822; %s
                Action: failed
                Status: %s
                Diagnostic-Code: smtp; %s User issue
                --bound
                Content-Type: message/rfc822

                Original message
                --bound--
                """.formatted(recipient, status, status);
    }
}
