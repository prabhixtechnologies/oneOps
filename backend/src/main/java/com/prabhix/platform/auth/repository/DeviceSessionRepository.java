package com.prabhix.platform.auth.repository;

import com.prabhix.platform.auth.domain.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    List<DeviceSession> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID userId);

    Optional<DeviceSession> findByUserIdAndDeviceIdAndRevokedAtIsNull(UUID userId, String deviceId);

    /** Live sessions across every tenant, for the ops hub. */
    long countByRevokedAtIsNull();
}
