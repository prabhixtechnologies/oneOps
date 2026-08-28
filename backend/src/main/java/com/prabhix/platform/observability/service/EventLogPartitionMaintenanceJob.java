package com.prabhix.platform.observability.service;

import com.prabhix.platform.observability.repository.EventLogRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogPartitionMaintenanceJob {

    private final EntityManager entityManager;

    @Scheduled(cron = "0 30 1 1 * *")
    @Transactional
    public void ensureFuturePartitions() {
        LocalDate start = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(1);
        for (int i = 0; i < 3; i++) {
            LocalDate from = start.plusMonths(i);
            LocalDate to = from.plusMonths(1);
            String table = partitionName(from);
            try {
                entityManager.createNativeQuery("""
                        CREATE TABLE IF NOT EXISTS %s PARTITION OF event_logs
                        FOR VALUES FROM ('%s') TO ('%s')
                        """.formatted(table, from, to))
                        .executeUpdate();
            } catch (Exception ex) {
                log.debug("Partition {} already exists or could not be created: {}", table, ex.getMessage());
            }
        }
    }

    private static String partitionName(LocalDate from) {
        return "event_logs_%d_%02d".formatted(from.getYear(), from.getMonthValue());
    }
}
