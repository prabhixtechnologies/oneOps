package com.prabhix.platform.visitor.service;

import com.prabhix.platform.visitor.config.VisitorProperties;
import com.prabhix.platform.visitor.domain.VisitorEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorDailyRollupJob {

    private final JdbcTemplate jdbc;
    private final VisitorProperties properties;

    @Scheduled(cron = "${prabhix.visitor.rollup-cron:0 5 2 * * *}")
    @Transactional
    public void run() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        List<UUID> orgIds = jdbc.queryForList(
                """
                SELECT DISTINCT organization_id FROM visitor_page_views
                WHERE viewed_at >= ? AND viewed_at < ?
                LIMIT 100
                """,
                UUID.class,
                yesterday.atStartOfDay(ZoneOffset.UTC).toInstant(),
                yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        for (UUID orgId : orgIds) {
            rollupOrg(orgId, yesterday);
        }
    }

    void rollupOrg(UUID orgId, LocalDate date) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int batch = properties.retentionBatchSize();

        upsertMetric(orgId, date, VisitorEnums.AggregateMetric.PAGE_VIEWS, "",
                countPageViews(orgId, start, end));
        upsertMetric(orgId, date, VisitorEnums.AggregateMetric.SESSIONS, "",
                countSessions(orgId, start, end));
        upsertMetric(orgId, date, VisitorEnums.AggregateMetric.UNIQUE_VISITORS, "",
                countUniqueVisitors(orgId, start, end));

        rollupTopPages(orgId, date, start, end, batch);
        rollupReferrers(orgId, date, start, end, batch);
        rollupConversionEvents(orgId, date, start, end);
    }

    private void rollupTopPages(UUID orgId, LocalDate date, Instant start, Instant end, int batch) {
        jdbc.query("""
                        SELECT path, COUNT(*) AS cnt FROM visitor_page_views
                        WHERE organization_id = ? AND viewed_at >= ? AND viewed_at < ?
                        GROUP BY path ORDER BY cnt DESC LIMIT ?
                        """,
                rs -> {
                    upsertMetric(orgId, date, VisitorEnums.AggregateMetric.PAGE_VIEWS,
                            rs.getString("path"), rs.getLong("cnt"));
                },
                orgId, start, end, batch);
    }

    private void rollupReferrers(UUID orgId, LocalDate date, Instant start, Instant end, int batch) {
        jdbc.query("""
                        SELECT COALESCE(NULLIF(referrer, ''), '(direct)') AS ref, COUNT(*) AS cnt
                        FROM visitor_sessions
                        WHERE organization_id = ? AND started_at >= ? AND started_at < ?
                        GROUP BY ref ORDER BY cnt DESC LIMIT ?
                        """,
                rs -> {
                    upsertMetric(orgId, date, VisitorEnums.AggregateMetric.SESSIONS,
                            rs.getString("ref"), rs.getLong("cnt"));
                },
                orgId, start, end, batch);
    }

    private void rollupConversionEvents(UUID orgId, LocalDate date, Instant start, Instant end) {
        Long leads = jdbc.queryForObject("""
                SELECT COUNT(*) FROM visitor_events
                WHERE organization_id = ? AND occurred_at >= ? AND occurred_at < ?
                  AND event_name = 'lead_submitted'
                """, Long.class, orgId, start, end);
        upsertMetric(orgId, date, VisitorEnums.AggregateMetric.EVENT, "lead_submitted",
                leads == null ? 0L : leads);

        Long chats = jdbc.queryForObject("""
                SELECT COUNT(*) FROM visitor_events
                WHERE organization_id = ? AND occurred_at >= ? AND occurred_at < ?
                  AND event_name = 'chat_started'
                """, Long.class, orgId, start, end);
        upsertMetric(orgId, date, VisitorEnums.AggregateMetric.EVENT, "chat_started",
                chats == null ? 0L : chats);
    }

    private long countPageViews(UUID orgId, Instant start, Instant end) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM visitor_page_views
                WHERE organization_id = ? AND viewed_at >= ? AND viewed_at < ?
                """, Long.class, orgId, start, end);
        return count == null ? 0L : count;
    }

    private long countSessions(UUID orgId, Instant start, Instant end) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM visitor_sessions
                WHERE organization_id = ? AND started_at >= ? AND started_at < ?
                """, Long.class, orgId, start, end);
        return count == null ? 0L : count;
    }

    private long countUniqueVisitors(UUID orgId, Instant start, Instant end) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT visitor_id) FROM visitor_page_views
                WHERE organization_id = ? AND viewed_at >= ? AND viewed_at < ?
                """, Long.class, orgId, start, end);
        return count == null ? 0L : count;
    }

    private void upsertMetric(UUID orgId, LocalDate date, VisitorEnums.AggregateMetric metricType,
                              String dimension, long count) {
        jdbc.update("""
                INSERT INTO visitor_daily_aggregates
                    (organization_id, aggregate_date, metric_type, dimension, count_value, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (organization_id, aggregate_date, metric_type, dimension)
                DO UPDATE SET count_value = EXCLUDED.count_value, updated_at = now()
                """, orgId, date, metricType.name(), dimension, count);
    }
}
