package com.prabhix.platform.push.repository;

import com.prabhix.platform.push.domain.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByOrganizationIdAndUserIdAndDeletedAtIsNullOrderByLastSeenAtDesc(
            UUID organizationId, UUID userId);

    List<PushToken> findByOrganizationIdAndUserIdAndEnabledTrueAndDeletedAtIsNull(
            UUID organizationId, UUID userId);
}
