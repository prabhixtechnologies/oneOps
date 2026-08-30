package com.prabhix.platform.visitor.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.visitor.config.VisitorProperties;
import com.prabhix.platform.visitor.dto.VisitorDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitorPresenceService {

    private static final String PRESENCE_PREFIX = "visitor:presence:";
    private static final String ORG_SET_PREFIX = "visitor:presence:org:";

    private final StringRedisTemplate redis;
    private final VisitorProperties properties;
    private final ObjectMapper objectMapper;

    public void touch(UUID organizationId, UUID visitorId, String externalKey,
                      VisitorDtos.PresenceUpdate presence, String email, String displayName) {
        Duration ttl = Duration.ofSeconds(properties.presenceTtlSeconds());
        String key = presenceKey(organizationId, visitorId);
        try {
            String json = objectMapper.writeValueAsString(new PresenceRecord(
                    visitorId, externalKey, presence.path(), presence.title(),
                    Instant.now().toString(), email, displayName));
            redis.opsForValue().set(key, json, ttl);
            redis.opsForSet().add(orgSetKey(organizationId), visitorId.toString());
            redis.expire(orgSetKey(organizationId), ttl.multipliedBy(2));
        } catch (JacksonException ignored) {
            // Presence is best-effort; ingest must not fail if Redis serialization breaks.
        }
    }

    public List<VisitorDtos.LiveVisitor> listLive(UUID organizationId) {
        Set<String> ids = redis.opsForSet().members(orgSetKey(organizationId));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<VisitorDtos.LiveVisitor> live = new ArrayList<>();
        for (String id : ids) {
            String json = redis.opsForValue().get(presenceKey(organizationId, UUID.fromString(id)));
            if (json == null) {
                redis.opsForSet().remove(orgSetKey(organizationId), id);
                continue;
            }
            try {
                PresenceRecord record = objectMapper.readValue(json, PresenceRecord.class);
                live.add(new VisitorDtos.LiveVisitor(
                        record.visitorId(), record.externalKey(), record.path(), record.title(),
                        Instant.parse(record.since()), record.email(), record.displayName()));
            } catch (Exception ignored) {
                redis.opsForSet().remove(orgSetKey(organizationId), id);
            }
        }
        return live;
    }

    private String presenceKey(UUID orgId, UUID visitorId) {
        return PRESENCE_PREFIX + orgId + ":" + visitorId;
    }

    private String orgSetKey(UUID orgId) {
        return ORG_SET_PREFIX + orgId;
    }

    private record PresenceRecord(
            UUID visitorId,
            String externalKey,
            String path,
            String title,
            String since,
            String email,
            String displayName) {
    }
}
