package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.mail.domain.MailInboundRaw;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.helpdesk.SlaService;
import com.prabhix.platform.mail.outbound.SuppressionService;
import com.prabhix.platform.mail.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailIngestionDsnTest {

    @Mock MailInboundRawRepository inboundRawRepository;
    @Mock MailMessageRepository messageRepository;
    @Mock MailAttachmentRepository attachmentRepository;
    @Mock MailThreadRepository threadRepository;
    @Mock MailThreadEventRepository eventRepository;
    @Mock MailboxRepository mailboxRepository;
    @Mock MailTagRepository tagRepository;
    @Mock MailThreadTagRepository threadTagRepository;
    @Mock FileStorageService fileStorageService;
    @Mock ThreadResolver threadResolver;
    @Mock com.prabhix.platform.mail.mailbox.MailFolderService folderService;
    @Mock com.prabhix.platform.mail.repository.MailThreadFlagRepository flagRepository;
    @Mock RoutingRuleEngine routingRuleEngine;
    @Mock SlaService slaService;
    @Mock SuppressionService suppressionService;
    @Mock ApplicationEventPublisher events;

    MailIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new MailIngestionService(
                inboundRawRepository, messageRepository, attachmentRepository, threadRepository,
                eventRepository, mailboxRepository, tagRepository, threadTagRepository,
                fileStorageService, new MimeParser(), threadResolver, folderService, flagRepository,
                routingRuleEngine, slaService, suppressionService, events);
    }

    @Test
    void dsnIsRoutedToSuppressionService() throws Exception {
        String dsn = """
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
                Final-Recipient: rfc822; bounced@recipient.com
                Action: failed
                Status: 5.1.1
                --bound
                Content-Type: message/rfc822

                Original
                --bound--
                """;
        byte[] bytes = dsn.getBytes(StandardCharsets.UTF_8);
        MailInboundRaw raw = new MailInboundRaw();
        raw.setId(UUID.randomUUID());
        raw.setOrganizationId(UUID.randomUUID());
        raw.setMailboxId(UUID.randomUUID());
        raw.setRawContent("b64:" + java.util.Base64.getEncoder().encodeToString(bytes));
        raw.setStatus(MailEnums.InboundRawStatus.PROCESSING);

        ingestionService.processOne(raw);

        verify(suppressionService).parseAndRecordDsn(anyString(), eq(raw.getOrganizationId()));
        verify(messageRepository, never()).save(any());
        verify(inboundRawRepository).save(argThat(r ->
                r.getStatus() == MailEnums.InboundRawStatus.PROCESSED));
    }
}
