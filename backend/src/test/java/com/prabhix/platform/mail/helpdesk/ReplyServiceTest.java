package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.service.AttachmentValidationService;
import com.prabhix.platform.mail.domain.*;
import com.prabhix.platform.mail.dto.ThreadDtos;
import com.prabhix.platform.mail.outbound.MailDispatcher;
import com.prabhix.platform.mail.repository.*;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplyServiceTest {

    @Mock MailThreadRepository threadRepository;
    @Mock MailMessageRepository messageRepository;
    @Mock MailAttachmentRepository attachmentRepository;
    @Mock MailboxRepository mailboxRepository;
    @Mock MailAliasRepository aliasRepository;
    @Mock StoredFileRepository storedFileRepository;
    @Mock MailDispatcher mailDispatcher;
    @Mock AssignmentService assignmentService;
    @Mock CannedReplyService cannedReplyService;
    @Mock SlaService slaService;
    @Mock AttachmentValidationService attachmentValidationService;

    ReplyService replyService;
    UUID orgId = UUID.randomUUID();
    UUID threadId = UUID.randomUUID();
    UUID mailboxId = UUID.randomUUID();
    PrabhixPrincipal principal = new PrabhixPrincipal(UUID.randomUUID(), "agent@acme.com",
            "Agent", orgId, Set.of(Permission.MAIL_SEND), UUID.randomUUID(), false);

    @BeforeEach
    void setUp() {
        PrabhixProperties props = TestProperties.defaults();
        replyService = new ReplyService(threadRepository, messageRepository, attachmentRepository,
                mailboxRepository, aliasRepository, storedFileRepository, mailDispatcher,
                assignmentService, cannedReplyService, slaService, attachmentValidationService, props);
    }

    @Test
    void replyAllExcludesOurAddressesFromCc() {
        stubThreadAndMailbox();
        MailMessage source = inboundMessage();
        source.setFromAddress("customer@example.com");
        source.setToAddresses(Json.toJson(List.of("support@acme.com", "customer@example.com")));
        source.setCcAddresses(Json.toJson(List.of("agent@acme.com", "other@example.com")));
        when(messageRepository.findFirstByThreadIdAndDeletedAtIsNullOrderByOccurredAtDesc(threadId))
                .thenReturn(Optional.of(source));
        when(aliasRepository.findByMailboxIdAndOrganizationId(mailboxId, orgId))
                .thenReturn(List.of(alias("alias@acme.com")));
        when(attachmentValidationService.requireCleanAttachments(eq(orgId), any())).thenReturn(List.of());
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ThreadDtos.ReplyRequest request = new ThreadDtos.ReplyRequest(
                MailEnums.ReplyMode.REPLY_ALL, null, null, null, "<p>Thanks</p>", List.of(), null);
        replyService.reply(principal, threadId, request);

        ArgumentCaptor<MailOutbox> outboxCaptor = ArgumentCaptor.forClass(MailOutbox.class);
        verify(mailDispatcher).enqueueDirect(outboxCaptor.capture());
        assertEquals(List.of("customer@example.com"), Json.parseStringList(outboxCaptor.getValue().getToAddresses()));
        List<String> cc = Json.parseStringList(outboxCaptor.getValue().getCcAddresses());
        assertTrue(cc.contains("other@example.com"));
        assertFalse(cc.stream().anyMatch(a -> a.equalsIgnoreCase("support@acme.com")));
        assertFalse(cc.stream().anyMatch(a -> a.equalsIgnoreCase("alias@acme.com")));
    }

    @Test
    void replyUsesReplyToWhenPresent() {
        stubThreadAndMailbox();
        MailMessage source = inboundMessage();
        source.setFromAddress("noreply@example.com");
        source.setReplyToAddress("support@example.com");
        when(messageRepository.findFirstByThreadIdAndDeletedAtIsNullOrderByOccurredAtDesc(threadId))
                .thenReturn(Optional.of(source));
        when(attachmentValidationService.requireCleanAttachments(eq(orgId), any())).thenReturn(List.of());
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ThreadDtos.ReplyRequest request = new ThreadDtos.ReplyRequest(
                MailEnums.ReplyMode.REPLY, null, null, null, "<p>Hi</p>", List.of(), null);
        replyService.reply(principal, threadId, request);

        ArgumentCaptor<MailOutbox> outboxCaptor = ArgumentCaptor.forClass(MailOutbox.class);
        verify(mailDispatcher).enqueueDirect(outboxCaptor.capture());
        assertEquals(List.of("support@example.com"),
                Json.parseStringList(outboxCaptor.getValue().getToAddresses()));
    }

    @Test
    void forwardWithoutRecipientsIs422() {
        stubThreadAndMailbox();
        when(messageRepository.findFirstByThreadIdAndDeletedAtIsNullOrderByOccurredAtDesc(threadId))
                .thenReturn(Optional.of(inboundMessage()));

        ThreadDtos.ReplyRequest request = new ThreadDtos.ReplyRequest(
                MailEnums.ReplyMode.FORWARD, List.of(), null, null, "<p>Fwd</p>", List.of(), null);

        ApiException ex = assertThrows(ApiException.class,
                () -> replyService.reply(principal, threadId, request));
        assertEquals(ErrorCode.INVALID_STATE, ex.getCode());
    }

    @Test
    void forwardIncludesQuotedHeadersAndAttachments() {
        stubThreadAndMailbox();
        MailMessage source = inboundMessage();
        source.setFromAddress("sender@example.com");
        source.setFromName("Sender");
        source.setSubject("Original");
        source.setBodyHtml("<p>Original body</p>");
        source.setToAddresses(Json.toJson(List.of("support@acme.com")));
        when(messageRepository.findFirstByThreadIdAndDeletedAtIsNullOrderByOccurredAtDesc(threadId))
                .thenReturn(Optional.of(source));
        UUID fileId = UUID.randomUUID();
        MailAttachment attachment = new MailAttachment();
        attachment.setFileId(fileId);
        attachment.setInline(false);
        when(attachmentRepository.findByMessageId(source.getId())).thenReturn(List.of(attachment));
        StoredFile file = new StoredFile();
        file.setId(fileId);
        file.setOriginalFilename("doc.pdf");
        file.setContentType("application/pdf");
        file.setSizeBytes(100);
        when(storedFileRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, orgId))
                .thenReturn(Optional.of(file));
        when(attachmentValidationService.requireCleanAttachments(eq(orgId), any())).thenReturn(List.of());
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ThreadDtos.ReplyRequest request = new ThreadDtos.ReplyRequest(
                MailEnums.ReplyMode.FORWARD, List.of("dest@example.com"), null, null,
                "<p>See below</p>", List.of(), null);
        replyService.reply(principal, threadId, request);

        ArgumentCaptor<MailMessage> msgCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(messageRepository).save(msgCaptor.capture());
        String html = msgCaptor.getValue().getBodyHtml();
        assertTrue(html.contains("Forwarded message"));
        assertTrue(html.contains("sender@example.com"));
        assertTrue(html.contains("Original"));
        verify(attachmentRepository, times(1)).save(any());
    }

    /**
     * The body is sent verbatim and the canned reply is only counted, because the agent has usually
     * edited the text. {@code usage_count} was shown in the admin list and never incremented, so every
     * canned reply read as unused no matter how often it went out.
     */
    @Test
    void replyCountsTheCannedReplyItStartedFrom() {
        stubThreadAndMailbox();
        MailMessage source = inboundMessage();
        source.setFromAddress("customer@example.com");
        when(messageRepository.findFirstByThreadIdAndDeletedAtIsNullOrderByOccurredAtDesc(threadId))
                .thenReturn(Optional.of(source));
        when(attachmentValidationService.requireCleanAttachments(eq(orgId), any())).thenReturn(List.of());
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID cannedReplyId = UUID.randomUUID();

        replyService.reply(principal, threadId, new ThreadDtos.ReplyRequest(
                MailEnums.ReplyMode.REPLY, null, null, null, "<p>Edited text</p>",
                List.of(), cannedReplyId));

        verify(cannedReplyService).recordUse(orgId, cannedReplyId);
        ArgumentCaptor<MailMessage> msgCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(messageRepository).save(msgCaptor.capture());
        assertTrue(msgCaptor.getValue().getBodyHtml().contains("Edited text"));
    }

    private void stubThreadAndMailbox() {
        MailThread thread = new MailThread();
        thread.setId(threadId);
        thread.setOrganizationId(orgId);
        thread.setMailboxId(mailboxId);
        thread.setSubject("Help");
        thread.setMessageCount(1);
        when(threadRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(threadId, orgId))
                .thenReturn(Optional.of(thread));
        Mailbox mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setOrganizationId(orgId);
        mailbox.setAddress("support@acme.com");
        mailbox.setName("Support");
        when(mailboxRepository.findById(mailboxId)).thenReturn(Optional.of(mailbox));
    }

    private MailMessage inboundMessage() {
        MailMessage msg = new MailMessage();
        msg.setId(UUID.randomUUID());
        msg.setMessageIdHeader("<inbound@test>");
        msg.setOccurredAt(Instant.parse("2026-08-27T10:00:00Z"));
        return msg;
    }

    private MailAlias alias(String address) {
        MailAlias alias = new MailAlias();
        alias.setAddress(address);
        return alias;
    }
}
