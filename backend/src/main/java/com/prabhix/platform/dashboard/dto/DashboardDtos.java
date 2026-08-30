package com.prabhix.platform.dashboard.dto;

import java.time.Instant;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
            Kpis kpis,
            List<ActivityItem> recentActivity,
            List<ChartPoint> ordersTrend,
            List<ChartPoint> visitorsTrend) {
    }

    /** Money is in the organization's minor unit, matching {@code currency}. */
    public record Kpis(
            long openConversations,
            long unassignedConversations,
            long visitorsToday,
            long ordersLast30Days,
            long revenueLast30Days,
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
