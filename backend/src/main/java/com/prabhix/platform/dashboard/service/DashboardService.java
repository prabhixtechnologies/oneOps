package com.prabhix.platform.dashboard.service;

import com.prabhix.platform.audit.domain.AuditLog;
import com.prabhix.platform.audit.repository.AuditLogRepository;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.dashboard.dto.DashboardDtos;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int TREND_DAYS = 14;
    private static final List<BillingEnums.SubscriptionStatus> LIVE_STATUSES = List.of(
            BillingEnums.SubscriptionStatus.TRIALING,
            BillingEnums.SubscriptionStatus.ACTIVE,
            BillingEnums.SubscriptionStatus.PAST_DUE,
            BillingEnums.SubscriptionStatus.PAUSED);

    private final MailThreadRepository threadRepository;
    private final OrganizationRepository organizationRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse getDashboard(UUID organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization not found"));

        Instant trendSince = Instant.now().minus(TREND_DAYS, ChronoUnit.DAYS);
        Instant breachSince = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant responseSince = Instant.now().minus(90, ChronoUnit.DAYS);

        DashboardDtos.Kpis kpis = new DashboardDtos.Kpis(
                threadRepository.countOpenThreads(organizationId),
                threadRepository.avgFirstResponseMinutes(organizationId, responseSince),
                threadRepository.countRecentSlaBreaches(organizationId, breachSince),
                org.getMemberCount(),
                org.getSeatLimit(),
                resolveMrr(organizationId),
                org.getCurrency());

        List<DashboardDtos.ActivityItem> activity = auditLogRepository
                .findRecentByOrganization(organizationId, PageRequest.of(0, 10))
                .stream()
                .map(this::toActivity)
                .toList();

        List<DashboardDtos.ChartPoint> threadsTrend = buildTrend(
                threadRepository.countThreadsByDay(organizationId, trendSince));
        List<DashboardDtos.ChartPoint> responseTrend = buildTrend(
                threadRepository.avgResponseMinutesByDay(organizationId, trendSince));

        return new DashboardDtos.DashboardResponse(kpis, activity, threadsTrend, responseTrend);
    }

    private long resolveMrr(UUID organizationId) {
        return subscriptionRepository.findByOrganizationIdAndStatusIn(organizationId, LIVE_STATUSES)
                .map(sub -> sub.getLockedAmountPaise()
                        + (long) sub.getSeats() * sub.getLockedPerSeatPaise())
                .orElse(0L);
    }

    private DashboardDtos.ActivityItem toActivity(AuditLog log) {
        String actor = log.getActorEmail();
        String description = log.getResourceLabel() != null && !log.getResourceLabel().isBlank()
                ? log.getResourceLabel()
                : log.getAction().replace('.', ' ');
        return new DashboardDtos.ActivityItem(
                log.getId().getId().toString(),
                log.getAction(),
                description,
                actor,
                log.getId().getCreatedAt());
    }

    private List<DashboardDtos.ChartPoint> buildTrend(List<Object[]> rows) {
        Map<LocalDate, Double> values = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = toLocalDate(row[0]);
            double value = row[1] instanceof Number number ? number.doubleValue() : 0.0;
            values.put(day, value);
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<DashboardDtos.ChartPoint> points = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            points.add(new DashboardDtos.ChartPoint(day.toString(), values.getOrDefault(day, 0.0)));
        }
        return points;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
