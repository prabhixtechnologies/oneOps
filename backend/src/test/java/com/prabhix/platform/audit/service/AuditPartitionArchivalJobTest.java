package com.prabhix.platform.audit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditPartitionArchivalJobTest {

    @Mock
    AuditPartitionArchiver archiver;
    @Mock
    StringRedisTemplate redis;
    @Mock
    ValueOperations<String, String> valueOps;

    AuditPartitionArchivalJob job;

    @BeforeEach
    void setUp() {
        job = new AuditPartitionArchivalJob(archiver, redis);
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "dryRun", false);
        ReflectionTestUtils.setField(job, "retention", Duration.ofDays(730));
    }

    @Test
    void skipsWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.archiveExpiredPartitions();

        verify(archiver, never()).findEligiblePartitions(any());
    }

    @Test
    void skipsWhenLockNotAcquired() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("pbx:job:audit-partition-archival"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        job.archiveExpiredPartitions();

        verify(archiver, never()).findEligiblePartitions(any());
    }

    @Test
    void processesEligiblePartitionsWhenLockAcquired() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("pbx:job:audit-partition-archival"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(archiver.findEligiblePartitions(any(Instant.class)))
                .thenReturn(List.of("audit_logs_2020_01"));

        job.archiveExpiredPartitions();

        verify(archiver).archiveAndDrop("audit_logs_2020_01", false);
        verify(redis).delete("pbx:job:audit-partition-archival");
    }
}
