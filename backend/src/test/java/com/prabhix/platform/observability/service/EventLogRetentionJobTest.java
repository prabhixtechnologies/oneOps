package com.prabhix.platform.observability.service;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.config.ObservabilityProperties;
import com.prabhix.platform.observability.repository.EventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventLogRetentionJobTest {

    @Mock
    EventLogRepository repository;
    @Mock
    StructuredEventLogger eventLogger;

    EventLogRetentionJob job;

    @BeforeEach
    void setUp() {
        ObservabilityProperties props = new ObservabilityProperties(
                "console", Duration.ofDays(90), "0 45 2 * * *", Duration.ofSeconds(1), 1.0);
        job = new EventLogRetentionJob(repository, props, eventLogger);
    }

    @Test
    void deletesInBoundedBatches() {
        when(repository.deleteOlderThanBatch(any(Instant.class), eq(500)))
                .thenReturn(500, 500, 100, 0);

        job.purgeExpired();

        verify(repository, times(3)).deleteOlderThanBatch(any(Instant.class), eq(500));
    }
}
