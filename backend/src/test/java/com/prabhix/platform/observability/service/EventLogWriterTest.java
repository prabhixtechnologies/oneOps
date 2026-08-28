package com.prabhix.platform.observability.service;

import com.prabhix.platform.observability.domain.EventLog;
import com.prabhix.platform.observability.event.EventLogRequested;
import com.prabhix.platform.observability.repository.EventLogRepository;
import com.prabhix.platform.observability.taxonomy.LogCategory;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.observability.taxonomy.LogSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventLogWriterTest {

    @Mock
    EventLogRepository repository;

    @InjectMocks
    EventLogWriter writer;

    @Test
    void persistsEventLog() {
        EventLogRequested event = sampleEvent();
        writer.onEventLogRequested(event);
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(repository).save(captor.capture());
        assertEquals(LogEventCode.AUTH_LOGIN_FAILED.code(), captor.getValue().getEventCode());
    }

    @Test
    void writeFailureDoesNotThrow() {
        doThrow(new RuntimeException("db down")).when(repository).save(any());
        assertDoesNotThrow(() -> writer.onEventLogRequested(sampleEvent()));
    }

    private EventLogRequested sampleEvent() {
        return new EventLogRequested(
                UUID.randomUUID(),
                LogEventCode.AUTH_LOGIN_FAILED,
                LogCategory.AUTH,
                LogSeverity.WARN,
                "abc123456789",
                UUID.randomUUID(),
                "USER",
                "Test User",
                null,
                null,
                Map.of("reason", "bad password"),
                "127.0.0.1",
                "JUnit",
                true,
                false);
    }
}
