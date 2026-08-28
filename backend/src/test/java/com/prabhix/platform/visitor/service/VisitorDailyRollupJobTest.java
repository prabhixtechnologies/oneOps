package com.prabhix.platform.visitor.service;

import com.prabhix.platform.visitor.config.VisitorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorDailyRollupJobTest {

    @Mock
    JdbcTemplate jdbc;

    VisitorDailyRollupJob job;

    private final UUID orgId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 8, 27);

    @BeforeEach
    void setUp() {
        job = new VisitorDailyRollupJob(jdbc, new VisitorProperties(null, 500, 65536, 120, 60, 120, null, 500));
    }

    @Test
    void rollupWritesPageViewTotal() {
        when(jdbc.queryForObject(contains("COUNT(*) FROM visitor_page_views"), eq(Long.class), any(), any(), any()))
                .thenReturn(42L);
        when(jdbc.queryForObject(contains("visitor_sessions"), eq(Long.class), any(), any(), any()))
                .thenReturn(10L);
        when(jdbc.queryForObject(contains("DISTINCT visitor_id"), eq(Long.class), any(), any(), any()))
                .thenReturn(7L);
        when(jdbc.queryForObject(contains("visitor_events"), eq(Long.class), any(), any(), any()))
                .thenReturn(0L);

        job.rollupOrg(orgId, date);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(contains("visitor_daily_aggregates"), argsCaptor.capture());
        boolean wrotePageViews = argsCaptor.getAllValues().stream()
                .anyMatch(args -> "PAGE_VIEWS".equals(args[2]) && "".equals(args[3]) && args[4].equals(42L));
        assertEquals(true, wrotePageViews);
    }
}
