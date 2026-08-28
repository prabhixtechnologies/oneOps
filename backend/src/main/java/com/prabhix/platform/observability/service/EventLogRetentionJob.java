package com.prabhix.platform.observability.service;

import com.prabhix.platform.observability.config.ObservabilityProperties;
import com.prabhix.platform.observability.repository.EventLogRepository;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogRetentionJob {

    private static final int BATCH_SIZE = 500;

    private final EventLogRepository repository;
    private final ObservabilityProperties properties;
    private final StructuredEventLogger eventLogger;

    @Scheduled(cron = "${prabhix.observability.event-log-retention-cron:0 45 2 * * *}")
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(properties.eventLogRetention());
        int total = 0;
        int deleted;
        do {
            deleted = repository.deleteOlderThanBatch(cutoff, BATCH_SIZE);
            total += deleted;
        } while (deleted == BATCH_SIZE);

        if (total > 0) {
            log.info("Purged {} expired event log rows older than {}", total, cutoff);
            eventLogger.log(LogEventCode.JOB_RETENTION_RUN, java.util.Map.of(
                    "table", "event_logs",
                    "deleted", total,
                    "cutoff", cutoff.toString()), false);
        }
    }
}
