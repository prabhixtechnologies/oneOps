package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailOutbox;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailDispatcherTest {

    @Mock MailOutboxRepository outboxRepository;

    MailDispatcher dispatcher;
    UUID existingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties props = TestProperties.defaults();
        dispatcher = new MailDispatcher(outboxRepository, props);
    }

    @Test
    void duplicateDedupeKeyEnqueuesOnce() {
        when(outboxRepository.insertWithDedupe(any(), any(), any(), any(), any(), any(), any(), any(),
                eq("dedupe-1"), anyInt(), anyInt())).thenReturn(null);
        when(outboxRepository.findByDedupeKey("dedupe-1")).thenReturn(Optional.of(existingOutbox()));

        UUID id = dispatcher.enqueue(UUID.randomUUID(), "tpl", "en", List.of("a@b.com"),
                Map.of(), "dedupe-1", 10);

        assertEquals(existingId, id);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void directDuplicateDedupeKeyIsNoOp() {
        when(outboxRepository.insertDirectWithDedupe(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), eq("reply-key"), anyInt(), anyInt()))
                .thenReturn(null);
        when(outboxRepository.findByDedupeKey("reply-key")).thenReturn(Optional.of(existingOutbox()));

        MailOutbox row = new MailOutbox();
        row.setOrganizationId(UUID.randomUUID());
        row.setDedupeKey("reply-key");
        row.setFromAddress("from@example.com");
        row.setToAddresses(Json.toJson(List.of("to@example.com")));
        row.setBodyHtml("<p>x</p>");

        UUID id = dispatcher.enqueueDirect(row);

        assertEquals(existingId, id);
        verify(outboxRepository, never()).save(any());
    }

    private MailOutbox existingOutbox() {
        MailOutbox row = new MailOutbox();
        row.setId(existingId);
        return row;
    }
}
