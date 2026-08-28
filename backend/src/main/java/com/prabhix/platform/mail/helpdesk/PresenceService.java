package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.mail.event.MailStreamEvent;
import com.prabhix.platform.mail.repository.MailThreadDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String VIEWERS_KEY = "mail:presence:viewers:";
    private static final String TYPING_KEY = "mail:presence:typing:";

    private final StringRedisTemplate redis;
    private final MailThreadDraftRepository draftRepository;
    private final ApplicationEventPublisher events;

    public void recordViewing(UUID organizationId, UUID threadId, UUID userId, String displayName) {
        String key = VIEWERS_KEY + threadId;
        redis.opsForHash().put(key, userId.toString(), displayName);
        redis.expire(key, TTL);
        events.publishEvent(new MailStreamEvent("presence", organizationId,
                java.util.Map.of("threadId", threadId, "userId", userId, "action", "viewing")));
    }

    public void recordTyping(UUID organizationId, UUID threadId, UUID userId) {
        String key = TYPING_KEY + threadId;
        redis.opsForSet().add(key, userId.toString());
        redis.expire(key, TTL);
        events.publishEvent(new MailStreamEvent("presence", organizationId,
                java.util.Map.of("threadId", threadId, "userId", userId, "action", "typing")));
    }

    public Set<String> viewers(UUID threadId) {
        return redis.opsForHash().entries(VIEWERS_KEY + threadId).values().stream()
                .map(Object::toString).collect(Collectors.toSet());
    }

    public boolean hasDraftLock(UUID threadId, UUID userId) {
        return draftRepository.findByThreadId(threadId).stream()
                .anyMatch(d -> !d.getAuthorUserId().equals(userId));
    }
}
