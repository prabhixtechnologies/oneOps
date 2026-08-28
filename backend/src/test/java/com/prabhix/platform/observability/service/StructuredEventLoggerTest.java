package com.prabhix.platform.observability.service;

import com.prabhix.platform.observability.event.EventLogRequested;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StructuredEventLoggerTest {

    @Mock private ApplicationEventPublisher events;
    @Mock private ObservabilityMetrics metrics;
    @Mock private EventLogWriter writer;

    @InjectMocks private StructuredEventLogger logger;

    @Test
    void logDefersPersistenceToTheCommitListener() {
        logger.log(LogEventCode.AUTH_LOGIN_SUCCEEDED, Map.of("userId", UUID.randomUUID()));

        verify(events).publishEvent(any(EventLogRequested.class));
        verifyNoInteractions(writer);
    }

    @Test
    void logWithPersistDisabledNeitherPublishesNorWrites() {
        logger.log(LogEventCode.AUTH_TOKEN_REFRESHED, Map.of("userId", UUID.randomUUID()), false);

        verifyNoInteractions(events);
        verifyNoInteractions(writer);
        verify(metrics).incrementEvent(LogEventCode.AUTH_TOKEN_REFRESHED);
    }

    @Test
    void logNowWritesThroughInsteadOfWaitingForACommitThatWillNotHappen() {
        logger.logNow(LogEventCode.AUTH_LOGIN_FAILED, Map.of("reason", "bad_password"));

        ArgumentCaptor<EventLogRequested> captor = ArgumentCaptor.forClass(EventLogRequested.class);
        verify(writer).writeNow(captor.capture());
        verify(events, never()).publishEvent(any(EventLogRequested.class));

        EventLogRequested written = captor.getValue();
        assertEquals(LogEventCode.AUTH_LOGIN_FAILED, written.eventCode());
        assertEquals("bad_password", written.payload().get("reason"));
    }

    @Test
    void logNowSwallowsWriteFailuresSoTheCallersOutcomeIsUnchanged() {
        doThrow(new RuntimeException("db down")).when(writer).writeNow(any());

        // The caller is normally about to throw its own exception; an observability problem
        // must not pre-empt it with a different one.
        logger.logNow(LogEventCode.AUTH_LOGIN_FAILED, Map.of("reason", "bad_password"));

        verify(writer).writeNow(any());
    }

    @Test
    void redactsSensitiveKeysBeforeTheyReachStorage() {
        logger.logNow(LogEventCode.AUTH_LOGIN_FAILED,
                Map.of("reason", "bad_password", "password", "hunter2"));

        ArgumentCaptor<EventLogRequested> captor = ArgumentCaptor.forClass(EventLogRequested.class);
        verify(writer).writeNow(captor.capture());

        Object stored = captor.getValue().payload().get("password");
        assertNotEquals("hunter2", stored, "raw password must not survive redaction");
    }
}
