package com.prabhix.platform.chat.service;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.chat.domain.ChatEnums;
import com.prabhix.platform.chat.dto.ChatDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageIdempotencyServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private ChatMessageIdempotencyService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        service = new ChatMessageIdempotencyService(redis, mapper);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void replayReturnsStoredResultWithoutSecondExecution() throws Exception {
        ChatDtos.MessageView result = sampleMessage();
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger executions = new AtomicInteger();

        when(valueOps.setIfAbsent(anyString(), eq("__PROCESSING__"), any(java.time.Duration.class)))
                .thenReturn(true)
                .thenReturn(false);
        when(valueOps.get(anyString())).thenReturn(mapper.writeValueAsString(result));

        service.execute(orgId, conversationId, "key-1", () -> {
            executions.incrementAndGet();
            return result;
        });
        ChatDtos.MessageView replay = service.execute(orgId, conversationId, "key-1", () -> {
            executions.incrementAndGet();
            return sampleMessage();
        });

        assertEquals(result.id(), replay.id());
        assertEquals(1, executions.get());
    }

    private ChatDtos.MessageView sampleMessage() {
        return new ChatDtos.MessageView(
                UUID.randomUUID(), ChatEnums.SenderType.VISITOR, null, "hello", null, Instant.now());
    }
}
