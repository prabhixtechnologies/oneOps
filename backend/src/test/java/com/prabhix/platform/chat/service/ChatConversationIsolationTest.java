package com.prabhix.platform.chat.service;

import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import com.prabhix.platform.visitor.service.VisitorPresenceService;
import com.prabhix.platform.visitor.service.VisitorStitchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatConversationIsolationTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatSettingsRepository settingsRepository;
    @Mock private VisitorStitchService visitorStitchService;
    @Mock private VisitorPresenceService presenceService;
    @Mock private VisitorRepository visitorRepository;
    @Mock private ChatMessageService messageService;
    @Mock private ObjectProvider<ChatMessageService> messageServiceProvider;
    @Mock private ChatTokenService tokenService;
    @Mock private EntitlementGate entitlements;
    @Mock private ApplicationEventPublisher events;

    private ChatConversationService conversationService;

    private final UUID orgA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        conversationService = new ChatConversationService(
                organizationRepository, conversationRepository, messageRepository,
                settingsRepository, visitorStitchService, presenceService, visitorRepository,
                messageServiceProvider, tokenService, entitlements, properties, events);
    }

    @Test
    void agentCannotReadForeignTenantConversation() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(conversationId, orgA))
                .thenReturn(Optional.empty());

        PrabhixPrincipal principal = new PrabhixPrincipal(
                UUID.randomUUID(), "agent@example.com", "Agent",
                orgA, Set.of(Permission.CHAT_READ), UUID.randomUUID(), false);

        assertThrows(ApiException.class, () -> conversationService.get(principal, conversationId));
    }

    @Test
    void agentWithoutReadAllCannotViewUnassignedConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID otherAgent = UUID.randomUUID();

        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);
        conversation.setOrganizationId(orgA);
        conversation.setAssignedAgentId(otherAgent);
        conversation.setStatus(ChatEnums.ConversationStatus.OPEN);

        when(conversationRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(conversationId, orgA))
                .thenReturn(Optional.of(conversation));

        PrabhixPrincipal principal = new PrabhixPrincipal(
                UUID.randomUUID(), "agent@example.com", "Agent",
                orgA, Set.of(Permission.CHAT_READ), UUID.randomUUID(), false);

        assertThrows(ApiException.class, () -> conversationService.get(principal, conversationId));
    }
}
