package com.prabhix.platform.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditPartitionArchivalJob {

    private static final String LOCK_KEY = "pbx:job:audit-partition-archival";
    private static final Duration LOCK_TTL = Duration.ofHours(2);

    private final AuditPartitionArchiver archiver;
    private final StringRedisTemplate redis;

    @Value("${prabhix.audit.archive.enabled:false}")
    private boolean enabled;

    @Value("${prabhix.audit.archive.dry-run:true}")
    private boolean dryRun;

    @Value("${prabhix.audit.archive.retention:P730D}")
    private Duration retention;

    @Scheduled(cron = "${prabhix.audit.archive.cron:0 0 3 1 * *}")
    public void archiveExpiredPartitions() {
        if (!enabled) {
            return;
        }
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Audit partition archival already running on another instance");
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(retention);
            List<String> partitions = archiver.findEligiblePartitions(cutoff);
            if (partitions.isEmpty()) {
                return;
            }
            log.info("Audit archival processing {} partition(s), dryRun={}", partitions.size(), dryRun);
            for (String partition : partitions) {
                try {
                    archiver.archiveAndDrop(partition, dryRun);
                } catch (RuntimeException ex) {
                    log.error("Audit archival failed for {}: {}", partition, ex.getMessage(), ex);
                }
            }
        } finally {
            redis.delete(LOCK_KEY);
        }
    }
}
