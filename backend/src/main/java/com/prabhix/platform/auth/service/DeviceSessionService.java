package com.prabhix.platform.auth.service;

import com.prabhix.platform.auth.domain.DeviceSession;
import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final DeviceSessionRepository deviceSessionRepository;

    @Transactional(readOnly = true)
    public List<DeviceSession> findActiveByUserId(UUID userId) {
        return deviceSessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<DeviceSession> findById(UUID sessionId) {
        return deviceSessionRepository.findById(sessionId);
    }

    @Transactional
    public DeviceSession save(DeviceSession session) {
        return deviceSessionRepository.save(session);
    }
}
