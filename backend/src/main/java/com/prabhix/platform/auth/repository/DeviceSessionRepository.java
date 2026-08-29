package com.prabhix.platform.auth.repository;

import com.prabhix.platform.auth.domain.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    List<DeviceSession> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID userId);

    Optional<DeviceSession> findByUserIdAndDeviceIdAndRevokedAtIsNull(UUID userId, String deviceId);

    /**
     * Looks up a session by the hash of its browser cookie.
     *
     * <p>Revoked rows are matched deliberately rather than filtered here, so the caller can tell a
     * signed-out session apart from an unknown cookie and clear the stale cookie in the response.
     */
    Optional<DeviceSession> findByCookieTokenHash(String cookieTokenHash);

    /** Live sessions across every tenant, for the ops hub. */
    long countByRevokedAtIsNull();
}
