package com.prabhix.platform.chat.repository;

import com.prabhix.platform.chat.domain.ChatSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatSettingsRepository extends JpaRepository<ChatSettings, UUID> {

    Optional<ChatSettings> findByOrganizationId(UUID organizationId);
}
