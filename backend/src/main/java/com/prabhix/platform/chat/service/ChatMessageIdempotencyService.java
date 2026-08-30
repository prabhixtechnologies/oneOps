package com.prabhix.platform.chat.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.chat.dto.ChatDtos;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageIdempotencyService {

    private static final String PREFIX = "pbx:chat:idempotency:";
    private static final String PROCESSING = "__PROCESSING__";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final int MAX_WAIT_MS = 15_000;
    private static final int POLL_MS = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ChatDtos.MessageView execute(UUID organizationId, UUID conversationId, String idempotencyKey,
                                        Supplier<ChatDtos.MessageView> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        String key = redisKey(organizationId, conversationId, idempotencyKey);

        Boolean acquired = redis.opsForValue().setIfAbsent(key, PROCESSING, LOCK_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            return waitForResult(key).orElseGet(() -> {
                // Lock expired between attempts; try once more to become leader.
                Boolean retry = redis.opsForValue().setIfAbsent(key, PROCESSING, LOCK_TTL);
                if (Boolean.TRUE.equals(retry)) {
                    return complete(key, action);
                }
                return waitForResult(key).orElseThrow(() ->
                        ApiException.of(ErrorCode.CONFLICT, "Could not complete idempotent send"));
            });
        }
        return complete(key, action);
    }

    private ChatDtos.MessageView complete(String key, Supplier<ChatDtos.MessageView> action) {
        try {
            ChatDtos.MessageView result = action.get();
            storeResult(key, result);
            return result;
        } catch (RuntimeException ex) {
            redis.delete(key);
            throw ex;
        }
    }

    private Optional<ChatDtos.MessageView> waitForResult(String key) {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (!PROCESSING.equals(value)) {
                return Optional.of(deserialize(value));
            }
            sleep(POLL_MS);
        }
        return Optional.empty();
    }

    private void storeResult(String key, ChatDtos.MessageView result) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(result), RESULT_TTL);
        } catch (JacksonException ex) {
            redis.delete(key);
            throw new IllegalStateException("Could not store idempotent result", ex);
        }
    }

    private ChatDtos.MessageView deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChatDtos.MessageView.class);
        } catch (JacksonException ex) {
            throw ApiException.of(ErrorCode.MALFORMED_REQUEST, "Stored idempotent result is corrupt");
        }
    }

    private static String redisKey(UUID orgId, UUID conversationId, String idempotencyKey) {
        return PREFIX + orgId + ":" + conversationId + ":" + idempotencyKey.trim();
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.of(ErrorCode.CONFLICT, "Idempotent send interrupted");
        }
    }
}
