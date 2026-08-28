package com.prabhix.platform.chat.service;

import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.service.ChatAiService;
import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.domain.ChatMessage;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatFirstResponderServiceTest {

    @Mock private ChatAiService chatAiService;
    @Mock private ChatMessageService messageService;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatSettingsRepository settingsRepository;
    @Mock private ChatMessageRepository messageRepository;

    private ChatFirstResponderService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChatFirstResponderService(
                chatAiService, messageService, conversationRepository, settingsRepository, messageRepository);
    }

    private ChatConversation conversation() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);
        conversation.setOrganizationId(orgId);
        return conversation;
    }

    private void availability(ChatEnums.Availability availability) {
        ChatSettings settings = new ChatSettings();
        settings.setAvailability(availability);
        when(settingsRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(settings));
    }

    @Test
    void repliesWhenOfflineAndFirstVisitorMessage() {
        ChatConversation conversation = conversation();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        availability(ChatEnums.Availability.OFFLINE);
        when(messageRepository.countByConversationIdAndSenderTypeAndDeletedAtIsNull(
                conversationId, ChatEnums.SenderType.VISITOR)).thenReturn(1L);

        ChatMessage visitorMessage = new ChatMessage();
        visitorMessage.setBody("Hello");
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(visitorMessage));

        when(chatAiService.firstResponder(orgId, conversationId, "Hello"))
                .thenReturn(new AiDtos.DraftSuggestion(true, "AI here to help", "gemini", "gemini-pro", false, false));

        service.maybeReply(conversationId, messageId);

        verify(messageService).sendAiReply(conversation, "AI here to help");
    }

    @Test
    void skipsWhenOrgIsOnline() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        availability(ChatEnums.Availability.ONLINE);

        service.maybeReply(conversationId, messageId);

        verify(chatAiService, never()).firstResponder(any(), any(), anyString());
    }

    @Test
    void skipsWhenAgentAlreadyAssigned() {
        ChatConversation conversation = conversation();
        conversation.setAssignedAgentId(UUID.randomUUID());
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        service.maybeReply(conversationId, messageId);

        verify(chatAiService, never()).firstResponder(any(), any(), anyString());
    }

    @Test
    void skipsWhenNotTheOpeningMessage() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        availability(ChatEnums.Availability.OFFLINE);
        when(messageRepository.countByConversationIdAndSenderTypeAndDeletedAtIsNull(
                conversationId, ChatEnums.SenderType.VISITOR)).thenReturn(4L);

        service.maybeReply(conversationId, messageId);

        verify(chatAiService, never()).firstResponder(any(), any(), anyString());
    }

    @Test
    void neverPropagatesProviderFailureToTheVisitor() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        availability(ChatEnums.Availability.OFFLINE);
        when(messageRepository.countByConversationIdAndSenderTypeAndDeletedAtIsNull(
                conversationId, ChatEnums.SenderType.VISITOR)).thenReturn(1L);

        ChatMessage visitorMessage = new ChatMessage();
        visitorMessage.setBody("Hello");
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(visitorMessage));
        when(chatAiService.firstResponder(orgId, conversationId, "Hello"))
                .thenThrow(new IllegalStateException("provider down"));

        service.maybeReply(conversationId, messageId);

        verify(messageService, never()).sendAiReply(any(), anyString());
    }
}
