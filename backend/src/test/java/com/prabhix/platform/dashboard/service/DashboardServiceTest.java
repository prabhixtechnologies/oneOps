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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private MailThreadRepository threadRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private BillingSubscriptionRepository subscriptionRepository;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void aggregatesTenantMetricsFromRepositories() {
        UUID orgId = UUID.randomUUID();

        Organization org = new Organization();
        org.setId(orgId);
        org.setMemberCount(42);
        org.setSeatLimit(100);
        org.setCurrency("INR");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        when(threadRepository.countOpenThreads(orgId)).thenReturn(78L);
        when(threadRepository.avgFirstResponseMinutes(eq(orgId), any())).thenReturn(23.0);
        when(threadRepository.countRecentSlaBreaches(eq(orgId), any())).thenReturn(3L);
        when(threadRepository.countThreadsByDay(eq(orgId), any())).thenReturn(List.of());
        when(threadRepository.avgResponseMinutesByDay(eq(orgId), any())).thenReturn(List.of());
        when(auditLogRepository.findRecentByOrganization(eq(orgId), any(Pageable.class)))
                .thenReturn(List.of());

        BillingSubscription subscription = new BillingSubscription();
        subscription.setLockedAmountPaise(100000L);
        subscription.setLockedPerSeatPaise(1000L);
        subscription.setSeats(10);
        when(subscriptionRepository.findByOrganizationIdAndStatusIn(eq(orgId), any()))
                .thenReturn(Optional.of(subscription));

        DashboardDtos.DashboardResponse response = dashboardService.getDashboard(orgId);

        assertEquals(78L, response.kpis().openThreads());
        assertEquals(23.0, response.kpis().avgFirstResponseMinutes());
        assertEquals(3L, response.kpis().slaBreaches());
        assertEquals(42, response.kpis().seatsUsed());
        assertEquals(100, response.kpis().seatsLimit());
        assertEquals(110000L, response.kpis().mrr());
        assertEquals("INR", response.kpis().currency());
        assertEquals(14, response.threadsTrend().size());
        assertEquals(14, response.responseTimeTrend().size());
    }
}
