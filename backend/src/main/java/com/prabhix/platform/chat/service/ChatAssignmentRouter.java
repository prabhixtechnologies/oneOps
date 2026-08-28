package com.prabhix.platform.chat.service;

import com.prabhix.platform.chat.domain.ChatConversation;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.domain.ChatSettings;
import com.prabhix.platform.chat.event.ChatConversationAssigned;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.chat.repository.ChatSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatAssignmentRouter {

    private final ChatSettingsRepository settingsRepository;
    private final ChatConversationRepository conversationRepository;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    @Transactional
    public void assignIfNeeded(ChatConversation conversation) {
        if (conversation.getAssignedAgentId() != null) {
            return;
        }
        ChatSettings settings = settingsRepository.findByOrganizationId(conversation.getOrganizationId())
                .orElse(null);
        if (settings == null || !settings.isAutoAssignEnabled()
                || settings.getAvailability() != ChatEnums.Availability.ONLINE) {
            return;
        }

        List<UUID> agents = jdbc.queryForList("""
                SELECT DISTINCT m.user_id
                FROM organization_memberships m
                JOIN roles r ON r.id = m.role_id
                JOIN role_permissions rp ON rp.role_id = r.id
                WHERE m.organization_id = ?
                  AND m.status = 'ACTIVE'
                  AND rp.permission_code = 'CHAT_REPLY'
                ORDER BY m.user_id
                """, UUID.class, conversation.getOrganizationId());
        if (agents.isEmpty()) {
            return;
        }

        int cursor = settings.getRoutingCursor();
        UUID selected = null;
        for (int attempt = 0; attempt < agents.size(); attempt++) {
            UUID candidate = agents.get((cursor + attempt) % agents.size());
            long open = conversationRepository.countOpenForAgent(conversation.getOrganizationId(), candidate);
            if (open < settings.getMaxConcurrentConversations()) {
                selected = candidate;
                settings.setRoutingCursor((cursor + attempt + 1) % agents.size());
                settingsRepository.save(settings);
                break;
            }
        }
        if (selected == null) {
            return;
        }

        conversation.setAssignedAgentId(selected);
        conversationRepository.save(conversation);
        events.publishEvent(new ChatConversationAssigned(
                conversation.getOrganizationId(), conversation.getId(), selected, null));
    }
}
