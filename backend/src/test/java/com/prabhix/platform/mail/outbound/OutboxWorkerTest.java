package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailDeliveryEventRepository;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.mail.outbound.transport.LoggingTransport;
import com.prabhix.platform.mail.outbound.transport.MailTransportRouter;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.common.util.OutboxBackoff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock
    MailOutboxRepository outboxRepository;
    @Mock
    MailDeliveryEventRepository deliveryEventRepository;
    @Mock
    TemplateRenderer templateRenderer;
    @Mock
    SuppressionService suppressionService;
    @Mock
    MailTransportRouter transportRouter;
    @Mock
    MailTrackingInjector trackingInjector;
    @Mock
    OutboundAttachmentResolver attachmentResolver;
    @Mock
    StructuredEventLogger eventLogger;

    OutboxWorker worker;

    @BeforeEach
    void setUp() {
        PrabhixProperties props = TestProperties.defaults();
        worker = new OutboxWorker(props, outboxRepository, deliveryEventRepository,
                templateRenderer, suppressionService, transportRouter, trackingInjector,
                attachmentResolver, eventLogger);
    }

    @Test
    void backoffIncreasesWithAttempts() {
        Duration d1 = OutboxBackoff.nextDelay(1);
        Duration d3 = OutboxBackoff.nextDelay(3);
        assertTrue(d3.compareTo(d1) >= 0);
    }

    @Test
    void suppressedAddressSkipsSend() {
        MailOutbox row = outbox();
        when(suppressionService.isSuppressed("blocked@example.com", row.getOrganizationId())).thenReturn(true);

        worker.processRow(row);

        assertEquals(MailEnums.OutboxStatus.SUPPRESSED, row.getStatus());
    }

    @Test
    void sendsWhenNotSuppressed() {
        MailOutbox row = outbox();
        when(suppressionService.isSuppressed(any(), any())).thenReturn(false);
        when(transportRouter.select()).thenReturn(new LoggingTransport());

        worker.processRow(row);

        assertEquals(MailEnums.OutboxStatus.SENT, row.getStatus());
        verify(deliveryEventRepository).save(any());
    }

    @Test
    void routerRefusalLeavesMailRetryableRatherThanSent() {
        MailOutbox row = outbox();
        when(suppressionService.isSuppressed(any(), any())).thenReturn(false);
        when(transportRouter.select())
                .thenThrow(new IllegalStateException("No mail transport can deliver"));

        worker.processRow(row);

        // The failure this guards against is not a lost row, it is a row that says SENT with
        // transport_used = LOGGING. Nobody goes looking for mail the outbox claims it delivered.
        assertEquals(MailEnums.OutboxStatus.FAILED, row.getStatus());
        assertNull(row.getSentAt());
        assertNull(row.getTransportUsed());
        assertEquals(1, row.getAttempts());
        assertNotNull(row.getNextAttemptAt());
    }

    @Test
    void exhaustedAttemptsEndDeadRatherThanSent() {
        MailOutbox row = outbox();
        row.setAttempts(row.getMaxAttempts() - 1);
        when(suppressionService.isSuppressed(any(), any())).thenReturn(false);
        when(transportRouter.select())
                .thenThrow(new IllegalStateException("No mail transport can deliver"));

        worker.processRow(row);

        assertEquals(MailEnums.OutboxStatus.DEAD, row.getStatus());
        assertNull(row.getSentAt());
    }

    private MailOutbox outbox() {
        MailOutbox row = new MailOutbox();
        row.setId(UUID.randomUUID());
        row.setOrganizationId(UUID.randomUUID());
        row.setFromAddress("noreply@example.com");
        row.setToAddresses(Json.toJson(List.of("blocked@example.com")));
        row.setSubject("Test");
        row.setBodyHtml("<p>Hi</p>");
        row.setBodyText("Hi");
        row.setMaxAttempts(6);
        row.setAttempts(0);
        row.setScheduledAt(Instant.now());
        return row;
    }
}
