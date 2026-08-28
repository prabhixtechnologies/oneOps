package com.prabhix.platform.chat.service;

import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.event.ChatStreamEvent;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.files.service.AttachmentValidationService;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.repository.MailboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOfflineMessageTest {

    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatSettingsRepository settingsRepository;
    @Mock private ChatTokenService tokenService;
    @Mock private ChatAssignmentRouter assignmentRouter;
    @Mock private AttachmentValidationService attachmentValidationService;
    @Mock private MailboxRepository mailboxRepository;
    @Mock private EntitlementGate entitlements;
    @Mock private ApplicationEventPublisher events;
    @Mock private ChatMessageIdempotencyService idempotencyService;

    private ChatMessageService messageService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID mailboxId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        messageService = new ChatMessageService(
                conversationRepository, messageRepository, settingsRepository,
                tokenService, assignmentRouter, attachmentValidationService,
                mailboxRepository, entitlements, properties, events, idempotencyService);
        when(idempotencyService.execute(any(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void offlineAvailabilityPublishesMailRequested() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);
        conversation.setOrganizationId(orgId);
        conversation.setVisitorName("Jane");
        conversation.setVisitorEmail("jane@example.com");
        conversation.setSubject("Help");
        conversation.setStatus(ChatEnums.ConversationStatus.OPEN);

        ChatSettings settings = new ChatSettings();
        settings.setOrganizationId(orgId);
        settings.setAvailability(ChatEnums.Availability.OFFLINE);
        settings.setOfflineMailboxId(mailboxId);

        Mailbox mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setAddress("support@example.com");

        when(conversationRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(conversationId, orgId))
                .thenReturn(Optional.of(conversation));
        when(settingsRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(settings));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            com.prabhix.platform.chat.domain.ChatMessage msg = inv.getArgument(0);
            msg.setId(UUID.randomUUID());
            return msg;
        });
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailboxRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(mailboxId, orgId))
                .thenReturn(Optional.of(mailbox));

        ChatTokenService.ConversationToken token = new ChatTokenService.ConversationToken(
                orgId, conversationId, UUID.randomUUID());
        when(tokenService.parse("tok")).thenReturn(token);

        messageService.sendVisitor("acme", conversationId, "tok",
                new ChatDtos.SendMessageRequest("Need help offline", null, null), null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(e -> e instanceof com.prabhix.platform.common.event.MailRequested));
    }

    /**
     * The internal flag is honoured for agents, so the visitor endpoint has to ignore it
     * outright. A visitor able to author a NOTE would write into the pane agents treat as
     * private, and it would be hidden from the visitor's own transcript.
     */
    @Test
    void visitorCannotAuthorAnInternalNote() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);
        conversation.setOrganizationId(orgId);
        conversation.setStatus(ChatEnums.ConversationStatus.OPEN);

        when(conversationRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(conversationId, orgId))
                .thenReturn(Optional.of(conversation));
        when(settingsRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<com.prabhix.platform.chat.domain.ChatMessage> saved =
                ArgumentCaptor.forClass(com.prabhix.platform.chat.domain.ChatMessage.class);
        when(messageRepository.save(saved.capture())).thenAnswer(inv -> {
            com.prabhix.platform.chat.domain.ChatMessage msg = inv.getArgument(0);
            msg.setId(UUID.randomUUID());
            return msg;
        });

        ChatTokenService.ConversationToken token = new ChatTokenService.ConversationToken(
                orgId, conversationId, UUID.randomUUID());
        when(tokenService.parse("tok")).thenReturn(token);

        messageService.sendVisitor("acme", conversationId, "tok",
                new ChatDtos.SendMessageRequest("Let me in", null, true), null);

        assertTrue(saved.getValue().getSenderType() == ChatEnums.SenderType.VISITOR,
                "visitor message must be stored as VISITOR regardless of the internal flag");
    }
}
