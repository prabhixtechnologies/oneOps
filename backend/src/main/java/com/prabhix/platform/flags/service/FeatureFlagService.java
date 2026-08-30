package com.prabhix.platform.flags.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.flags.domain.FeatureFlag;
import com.prabhix.platform.flags.domain.FeatureFlagOverride;
import com.prabhix.platform.flags.dto.FlagDtos;
import com.prabhix.platform.flags.repository.FeatureFlagOverrideRepository;
import com.prabhix.platform.flags.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private static final String CACHE_PREFIX = "flags:org:";

    private final FeatureFlagRepository flagRepository;
    private final FeatureFlagOverrideRepository overrideRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID organizationId, String key) {
        return effectiveFlags(organizationId).getOrDefault(key, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> effectiveFlags(UUID organizationId) {
        String cacheKey = CACHE_PREFIX + organizationId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                // reload below
            }
        }

        List<FeatureFlag> defaults = flagRepository.findAllByOrderByFlagKeyAsc();
        Map<String, Boolean> overrides = overrideRepository.findByOrganizationId(organizationId)
                .stream()
                .collect(Collectors.toMap(FeatureFlagOverride::getFlagKey, FeatureFlagOverride::isEnabled));

        Map<String, Boolean> effective = new HashMap<>();
        for (FeatureFlag flag : defaults) {
            effective.put(flag.getFlagKey(),
                    overrides.getOrDefault(flag.getFlagKey(), flag.isDefaultEnabled()));
        }

        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(effective), Duration.ofMinutes(15));
        } catch (Exception ignored) {
            // cache is best-effort
        }
        return effective;
    }

    @Transactional(readOnly = true)
    public FlagDtos.EffectiveFlagsDetailed effectiveFlagsDetailed(UUID organizationId) {
        List<FeatureFlag> defaults = flagRepository.findAllByOrderByFlagKeyAsc();
        Map<String, FeatureFlagOverride> overrides = overrideRepository.findByOrganizationId(organizationId)
                .stream()
                .collect(Collectors.toMap(FeatureFlagOverride::getFlagKey, o -> o));

        List<FlagDtos.FlagDetail> details = new ArrayList<>();
        for (FeatureFlag flag : defaults) {
            FeatureFlagOverride override = overrides.get(flag.getFlagKey());
            if (override != null) {
                details.add(new FlagDtos.FlagDetail(
                        flag.getFlagKey(), override.isEnabled(), "OVERRIDE", flag.getDescription()));
            } else {
                details.add(new FlagDtos.FlagDetail(
                        flag.getFlagKey(), flag.isDefaultEnabled(), "DEFAULT", flag.getDescription()));
            }
        }
        return new FlagDtos.EffectiveFlagsDetailed(details);
    }

    @Transactional
    public FlagDtos.FlagDetail setOverride(UUID organizationId, UUID actorId, String flagKey,
                                           FlagDtos.SetOverrideRequest request) {
        FeatureFlag flag = flagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> com.prabhix.platform.common.error.ApiException.notFound("Flag"));
        FeatureFlagOverride override = overrideRepository
                .findByOrganizationIdAndFlagKey(organizationId, flagKey)
                .orElseGet(FeatureFlagOverride::new);
        override.setOrganizationId(organizationId);
        override.setFlagKey(flagKey);
        override.setEnabled(request.enabled());
        override.setReason(request.reason());
        overrideRepository.save(override);
        evictCache(organizationId);
        events.publishEvent(AuditRequested.changed(organizationId, actorId,
                "flags.override.set", "feature_flag", flag.getId(),
                Map.of("flagKey", flagKey, "enabled", request.enabled())));
        return new FlagDtos.FlagDetail(flagKey, request.enabled(), "OVERRIDE", flag.getDescription());
    }

    @Transactional
    public FlagDtos.FlagDetail clearOverride(UUID organizationId, UUID actorId, String flagKey) {
        FeatureFlag flag = flagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> com.prabhix.platform.common.error.ApiException.notFound("Flag"));
        overrideRepository.findByOrganizationIdAndFlagKey(organizationId, flagKey)
                .ifPresent(overrideRepository::delete);
        evictCache(organizationId);
        events.publishEvent(AuditRequested.of(organizationId, actorId,
                "flags.override.cleared", "feature_flag", flag.getId()));
        return new FlagDtos.FlagDetail(flagKey, flag.isDefaultEnabled(), "DEFAULT", flag.getDescription());
    }

    public void evictCache(UUID organizationId) {
        redis.delete(CACHE_PREFIX + organizationId);
    }
}
