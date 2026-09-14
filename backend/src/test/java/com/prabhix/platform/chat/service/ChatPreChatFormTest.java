package com.prabhix.platform.chat.service;

import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatMessageRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.common.spi.EntitlementGate;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import com.prabhix.platform.visitor.service.VisitorPresenceService;
import com.prabhix.platform.visitor.service.VisitorStitchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code ChatSettings.preChatEnabled} is switchable from the console, so the requirement for a
 * name and email has to live here rather than as bean validation on the request. These tests exist
 * because it was previously {@code @NotBlank} on the DTO, which made the setting dead: turning the
 * pre-chat form off left visitors unable to start a conversation at all.
 */
@ExtendWith(MockitoExtension.class)
class ChatPreChatFormTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatSettingsRepository settingsRepository;
    @Mock private VisitorStitchService visitorStitchService;
    @Mock private VisitorPresenceService presenceService;
    @Mock private VisitorRepository visitorRepository;
    @Mock private ObjectProvider<ChatMessageService> messageService;
    @Mock private ChatTokenService tokenService;
    @Mock private EntitlementGate entitlements;
    @Mock private ApplicationEventPublisher events;
    @Mock private MailClient mail;
    @Mock private StructuredEventLogger eventLogger;

    private ChatConversationService service;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        service = new ChatConversationService(
                organizationRepository, conversationRepository, messageRepository,
                settingsRepository, visitorStitchService, presenceService, visitorRepository,
                messageService, tokenService, entitlements, properties, events, mail, eventLogger);

        Organization org = new Organization();
        org.setId(orgId);
        org.setSlug("acme");
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(org));
    }

    private void preChat(boolean enabled) {
        ChatSettings settings = new ChatSettings();
        settings.setOrganizationId(orgId);
        settings.setPreChatEnabled(enabled);
        when(settingsRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(settings));
    }

    private ArgumentCaptor<ChatConversation> captureSave() {
        ArgumentCaptor<ChatConversation> saved = ArgumentCaptor.forClass(ChatConversation.class);
        when(conversationRepository.save(saved.capture())).thenAnswer(inv -> {
            ChatConversation c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        return saved;
    }

    @Test
    void requiresNameAndEmailWhenThePreChatFormIsOn() {
        preChat(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.startPublic("acme",
                new ChatDtos.PreChatRequest(null, null, "Quick question", null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    void treatsBlankNameAndEmailAsMissing() {
        preChat(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.startPublic("acme",
                new ChatDtos.PreChatRequest("   ", "  ", null, null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    void allowsAnAnonymousStartWhenThePreChatFormIsOff() {
        preChat(false);
        ArgumentCaptor<ChatConversation> saved = captureSave();

        var response = service.startPublic("acme",
                new ChatDtos.PreChatRequest(null, null, "Quick question", null));

        assertEquals(saved.getValue().getId(), response.conversationId());
        assertNull(saved.getValue().getVisitorName());
        assertNull(saved.getValue().getVisitorEmail(),
                "an anonymous conversation must not store an empty-string email");
    }

    @Test
    void storesEmailLowercasedWhenSupplied() {
        preChat(true);
        ArgumentCaptor<ChatConversation> saved = captureSave();

        service.startPublic("acme",
                new ChatDtos.PreChatRequest("Ramesh Kumar", "Ramesh.Kumar@Acme.IN", null, null));

        assertEquals("ramesh.kumar@acme.in", saved.getValue().getVisitorEmail());
        assertEquals("Ramesh Kumar", saved.getValue().getVisitorName());
    }
}
