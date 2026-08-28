package com.prabhix.platform.dashboard.dto;

import java.time.Instant;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
            Kpis kpis,
            List<ActivityItem> recentActivity,
            List<ChartPoint> threadsTrend,
            List<ChartPoint> responseTimeTrend) {
    }

    public record Kpis(
            long openThreads,
            double avgFirstResponseMinutes,
            long slaBreaches,
            int seatsUsed,
            int seatsLimit,
            long mrr,
            String currency) {
    }

    public record ActivityItem(
            String id,
            String type,
            String description,
            String actor,
            Instant createdAt) {
    }

    public record ChartPoint(String date, double value) {
    }
}
