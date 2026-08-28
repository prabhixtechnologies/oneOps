package com.prabhix.platform.visitor.service;

import com.prabhix.platform.visitor.config.VisitorProperties;
import com.prabhix.platform.visitor.repository.VisitorEventRepository;
import com.prabhix.platform.visitor.repository.VisitorPageViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorRetentionJob {

    private final JdbcTemplate jdbc;
    private final VisitorPageViewRepository pageViewRepository;
    private final VisitorEventRepository eventRepository;
    private final VisitorProperties properties;

    @Scheduled(cron = "${prabhix.visitor.retention-cron:0 15 2 * * *}")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(properties.rawRetention());
        List<UUID> orgIds = jdbc.queryForList(
                "SELECT DISTINCT organization_id FROM visitor_page_views WHERE viewed_at < ? LIMIT 100",
                UUID.class, cutoff);
        for (UUID orgId : orgIds) {
            pruneOrg(orgId, cutoff);
        }
    }

    private void pruneOrg(UUID orgId, Instant cutoff) {
        int batch = properties.retentionBatchSize();
        int deletedViews;
        int deletedEvents;
        do {
            deletedViews = pageViewRepository.deleteBatchBefore(orgId, cutoff, batch);
            deletedEvents = eventRepository.deleteBatchBefore(orgId, cutoff, batch);
        } while (deletedViews == batch || deletedEvents == batch);
        if (deletedViews > 0 || deletedEvents > 0) {
            log.info("Pruned visitor raw data for org {} before {}", orgId, cutoff);
        }
    }
}
