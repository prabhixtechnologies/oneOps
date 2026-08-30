package com.prabhix.platform.dashboard.service;

import com.prabhix.platform.audit.repository.AuditLogRepository;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.repository.BillingSubscriptionRepository;
import com.prabhix.platform.chat.repository.ChatConversationRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.dashboard.dto.DashboardDtos;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.visitor.repository.VisitorSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private BillingSubscriptionRepository subscriptionRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private VisitorSessionRepository visitorSessionRepository;
    @Mock private CommerceOrderRepository orderRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void aggregatesTenantMetricsFromRepositories() {
        UUID orgId = UUID.randomUUID();
        stubOrganization(orgId);

        when(conversationRepository.countOpen(orgId)).thenReturn(12L);
        when(conversationRepository.countUnassigned(orgId)).thenReturn(4L);
        when(visitorSessionRepository.countSessionsSince(eq(orgId), any())).thenReturn(310L);
        when(orderRepository.countOrdersSince(eq(orgId), any())).thenReturn(57L);
        when(orderRepository.sumRevenueSince(eq(orgId), any())).thenReturn(4_500_00L);
        when(orderRepository.countOrdersByDay(eq(orgId), any())).thenReturn(List.of());
        when(visitorSessionRepository.countSessionsByDay(eq(orgId), any())).thenReturn(List.of());
        when(auditLogRepository.findRecentByOrganization(eq(orgId), any(Pageable.class)))
                .thenReturn(List.of());

        BillingSubscription subscription = new BillingSubscription();
        subscription.setLockedAmountPaise(100000L);
        subscription.setLockedPerSeatPaise(1000L);
        subscription.setSeats(10);
        when(subscriptionRepository.findByOrganizationIdAndStatusIn(eq(orgId), any()))
                .thenReturn(Optional.of(subscription));

        DashboardDtos.DashboardResponse response = dashboardService.getDashboard(orgId);

        assertEquals(12L, response.kpis().openConversations());
        assertEquals(4L, response.kpis().unassignedConversations());
        assertEquals(310L, response.kpis().visitorsToday());
        assertEquals(57L, response.kpis().ordersLast30Days());
        assertEquals(4_500_00L, response.kpis().revenueLast30Days());
        assertEquals(42, response.kpis().seatsUsed());
        assertEquals(100, response.kpis().seatsLimit());
        assertEquals(110000L, response.kpis().mrr());
        assertEquals("INR", response.kpis().currency());
        assertEquals(14, response.ordersTrend().size());
        assertEquals(14, response.visitorsTrend().size());
    }

    /**
     * A day with no orders still has to appear, otherwise the sparkline silently compresses the
     * quiet days out and every gap reads as a shorter, busier fortnight than it was.
     */
    @Test
    void trendsFillTheDaysThatHaveNoRows() {
        UUID orgId = UUID.randomUUID();
        stubOrganization(orgId);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(orderRepository.countOrdersByDay(eq(orgId), any()))
                .thenReturn(List.<Object[]>of(new Object[]{java.sql.Date.valueOf(today), 9L}));
        when(visitorSessionRepository.countSessionsByDay(eq(orgId), any())).thenReturn(List.of());
        when(auditLogRepository.findRecentByOrganization(eq(orgId), any(Pageable.class)))
                .thenReturn(List.of());
        when(subscriptionRepository.findByOrganizationIdAndStatusIn(eq(orgId), any()))
                .thenReturn(Optional.empty());

        DashboardDtos.DashboardResponse response = dashboardService.getDashboard(orgId);

        List<DashboardDtos.ChartPoint> orders = response.ordersTrend();
        assertEquals(14, orders.size());
        assertEquals(today.toString(), orders.get(13).date());
        assertEquals(9.0, orders.get(13).value());
        assertEquals(0.0, orders.get(0).value());
        assertEquals(0L, response.kpis().mrr());
    }

    private void stubOrganization(UUID orgId) {
        Organization org = new Organization();
        org.setId(orgId);
        org.setMemberCount(42);
        org.setSeatLimit(100);
        org.setCurrency("INR");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
    }
}
