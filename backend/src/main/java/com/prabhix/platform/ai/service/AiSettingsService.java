package com.prabhix.platform.ai.service;

import com.prabhix.platform.ai.domain.AiOrgSettings;
import com.prabhix.platform.ai.dto.AiDtos;
import com.prabhix.platform.ai.repository.AiOrgSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiOrgSettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public AiDtos.OrgSettingsView get(UUID organizationId) {
        return settingsRepository.findByOrganizationId(organizationId)
                .map(this::toView)
                .orElse(new AiDtos.OrgSettingsView(null, null, null, false));
    }

    @Transactional
    public AiDtos.OrgSettingsView update(UUID organizationId, UUID userId,
                                         AiDtos.UpdateOrgSettingsRequest request) {
        AiOrgSettings settings = settingsRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    AiOrgSettings created = new AiOrgSettings();
                    created.setOrganizationId(organizationId);
                    return created;
                });
        if (request.preferredProvider() != null) {
            settings.setPreferredProvider(blankToNull(request.preferredProvider()));
        }
        if (request.preferredChatModel() != null) {
            settings.setPreferredChatModel(blankToNull(request.preferredChatModel()));
        }
        if (request.preferredReasoningModel() != null) {
            settings.setPreferredReasoningModel(blankToNull(request.preferredReasoningModel()));
        }
        if (request.firstResponderEnabled() != null) {
            settings.setFirstResponderEnabled(request.firstResponderEnabled());
        }
        return toView(settingsRepository.save(settings));
    }

    private AiDtos.OrgSettingsView toView(AiOrgSettings settings) {
        return new AiDtos.OrgSettingsView(
                settings.getPreferredProvider(),
                settings.getPreferredChatModel(),
                settings.getPreferredReasoningModel(),
                settings.isFirstResponderEnabled());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
