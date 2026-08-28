package com.prabhix.platform.chat.service;

import com.prabhix.platform.chat.domain.ChatCannedReply;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.chat.repository.ChatCannedReplyRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSettingsService {

    private final ChatSettingsRepository settingsRepository;
    private final ChatCannedReplyRepository cannedReplyRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public ChatDtos.SettingsView get(PrabhixPrincipal principal) {
        return toView(settingsOrDefault(principal.requireOrganizationId()));
    }

    @Transactional
    public ChatDtos.SettingsView update(PrabhixPrincipal principal, ChatDtos.SettingsUpdateRequest request) {
        UUID orgId = principal.requireOrganizationId();
        ChatSettings settings = settingsOrDefault(orgId);
        if (request.availability() != null) {
            settings.setAvailability(request.availability());
        }
        if (request.awayMessage() != null) {
            settings.setAwayMessage(request.awayMessage());
        }
        if (request.businessHours() != null) {
            settings.setBusinessHours(request.businessHours());
        }
        if (request.preChatEnabled() != null) {
            settings.setPreChatEnabled(request.preChatEnabled());
        }
        if (request.offlineMailboxId() != null) {
            settings.setOfflineMailboxId(request.offlineMailboxId());
        }
        if (request.autoAssignEnabled() != null) {
            settings.setAutoAssignEnabled(request.autoAssignEnabled());
        }
        if (request.maxConcurrentConversations() != null) {
            settings.setMaxConcurrentConversations(request.maxConcurrentConversations());
        }
        settings = settingsRepository.save(settings);
        events.publishEvent(AuditRequested.of(orgId, principal.userId(),
                "chat.settings.updated", "chat_settings", settings.getId()));
        return toView(settings);
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.CannedReplyView> listCannedReplies(PrabhixPrincipal principal) {
        return cannedReplyRepository.findByOrganizationIdAndDeletedAtIsNullOrderByTitleAsc(
                        principal.requireOrganizationId())
                .stream().map(this::toCanned).toList();
    }

    @Transactional
    public ChatDtos.CannedReplyView createCannedReply(PrabhixPrincipal principal,
                                                      ChatDtos.CannedReplyRequest request) {
        ChatCannedReply reply = new ChatCannedReply();
        reply.setOrganizationId(principal.requireOrganizationId());
        reply.setShortcut(request.shortcut());
        reply.setTitle(request.title());
        reply.setBody(request.body());
        reply = cannedReplyRepository.save(reply);
        return toCanned(reply);
    }

    @Transactional
    public void deleteCannedReply(PrabhixPrincipal principal, UUID id) {
        ChatCannedReply reply = cannedReplyRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(id, principal.requireOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Canned reply"));
        reply.setDeletedAt(Instant.now());
        cannedReplyRepository.save(reply);
    }

    private ChatSettings settingsOrDefault(UUID orgId) {
        return settingsRepository.findByOrganizationId(orgId).orElseGet(() -> {
            ChatSettings settings = new ChatSettings();
            settings.setOrganizationId(orgId);
            return settingsRepository.save(settings);
        });
    }

    private ChatDtos.SettingsView toView(ChatSettings settings) {
        return new ChatDtos.SettingsView(
                settings.getAvailability(),
                settings.getAwayMessage(),
                settings.getBusinessHours(),
                settings.isPreChatEnabled(),
                settings.getOfflineMailboxId(),
                settings.isAutoAssignEnabled(),
                settings.getMaxConcurrentConversations());
    }

    private ChatDtos.CannedReplyView toCanned(ChatCannedReply reply) {
        return new ChatDtos.CannedReplyView(reply.getId(), reply.getShortcut(), reply.getTitle(), reply.getBody());
    }
}
