package com.prabhix.platform.audit.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditPartitionMaintenanceJob {

    private static final DateTimeFormatter PARTITION = DateTimeFormatter.ofPattern("yyyy_MM");

    private final EntityManager entityManager;

    @Scheduled(cron = "${prabhix.audit.partition-cron:0 30 0 * * *}")
    @Transactional
    public void ensureNextMonthPartition() {
        LocalDate nextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        LocalDate monthAfter = nextMonth.plusMonths(1);
        String partitionName = "audit_logs_" + nextMonth.format(PARTITION);
        String from = nextMonth.toString();
        String to = monthAfter.toString();

        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS %s PARTITION OF audit_logs
                FOR VALUES FROM ('%s') TO ('%s')
                """.formatted(partitionName, from, to))
                .executeUpdate();
        log.info("Ensured audit partition {} for [{} .. {})", partitionName, from, to);
    }
}
