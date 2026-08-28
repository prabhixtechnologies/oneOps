package com.prabhix.platform.push.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.push.domain.PushToken;
import com.prabhix.platform.push.dto.PushDtos;
import com.prabhix.platform.push.repository.PushTokenRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository tokenRepository;

    @Transactional
    public PushDtos.RegisterPushTokenResponse register(PrabhixPrincipal principal,
                                                       PushDtos.RegisterPushTokenRequest request) {
        UUID orgId = principal.requireOrganizationId();
        UUID userId = principal.userId();
        Instant now = Instant.now();

        PushToken row = tokenRepository.findByToken(request.token()).orElse(null);
        if (row != null) {
            row.setOrganizationId(orgId);
            row.setUserId(userId);
            row.setPlatform(request.platform());
            row.setDeviceId(request.deviceId());
            row.setDeviceName(request.deviceName());
            row.setAppVersion(request.appVersion());
            row.setLastSeenAt(now);
            row.setEnabled(true);
            row.setDeletedAt(null);
            return new PushDtos.RegisterPushTokenResponse(tokenRepository.save(row).getId());
        }

        row = new PushToken();
        row.setOrganizationId(orgId);
        row.setUserId(userId);
        row.setToken(request.token());
        row.setPlatform(request.platform());
        row.setDeviceId(request.deviceId());
        row.setDeviceName(request.deviceName());
        row.setAppVersion(request.appVersion());
        row.setLastSeenAt(now);
        row.setEnabled(true);
        return new PushDtos.RegisterPushTokenResponse(tokenRepository.save(row).getId());
    }

    @Transactional
    public void deregister(PrabhixPrincipal principal, String token) {
        UUID orgId = principal.requireOrganizationId();
        PushToken row = tokenRepository.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("Device"));
        if (!row.getOrganizationId().equals(orgId) || !row.getUserId().equals(principal.userId())) {
            throw ApiException.forbidden("That device is not registered to you");
        }
        row.setEnabled(false);
        row.setDeletedAt(Instant.now());
        tokenRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<PushDtos.DeviceView> listMine(PrabhixPrincipal principal) {
        UUID orgId = principal.requireOrganizationId();
        return tokenRepository.findByOrganizationIdAndUserIdAndDeletedAtIsNullOrderByLastSeenAtDesc(
                        orgId, principal.userId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public void disableTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            tokenRepository.findByToken(token).ifPresent(row -> {
                row.setEnabled(false);
                row.setDeletedAt(Instant.now());
                tokenRepository.save(row);
            });
        }
    }

    private PushDtos.DeviceView toView(PushToken token) {
        return new PushDtos.DeviceView(
                token.getId(),
                token.getPlatform(),
                token.getDeviceId(),
                token.getDeviceName(),
                token.getAppVersion(),
                token.getLastSeenAt(),
                token.isEnabled());
    }
}
