package com.prabhix.platform.ops.service;

import com.prabhix.platform.auth.repository.DeviceSessionRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.observability.repository.EventLogRepository;
import com.prabhix.platform.ops.dto.OpsDtos;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.domain.Organization.OrganizationStatus;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.support.TestProperties;
import com.prabhix.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformOverviewServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailOutboxRepository mailOutboxRepository;
    @Mock private DeviceSessionRepository deviceSessionRepository;
    @Mock private EventLogRepository eventLogRepository;

    private PlatformOverviewService service;

    @BeforeEach
    void setUp() {
        service = new PlatformOverviewService(
                organizationRepository, userRepository, mailOutboxRepository,
                deviceSessionRepository, eventLogRepository, TestProperties.defaults());
    }

    @Test
    void overviewReportsCountsFromEveryTenant() {
        when(organizationRepository.countByDeletedAtIsNull()).thenReturn(12L);
        when(organizationRepository.countByStatusAndDeletedAtIsNull(any())).thenReturn(3L);
        when(organizationRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(any()))
                .thenReturn(2L);
        when(userRepository.countByDeletedAtIsNull()).thenReturn(40L);
        when(userRepository.countByStatusAndDeletedAtIsNull(any())).thenReturn(10L);
        when(userRepository.countByLockedUntilAfterAndDeletedAtIsNull(any())).thenReturn(1L);
        when(userRepository.countByPlatformAdminTrueAndDeletedAtIsNull()).thenReturn(2L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(any())).thenReturn(5L);
        when(mailOutboxRepository.countByStatusIn(any())).thenReturn(7L);
        when(deviceSessionRepository.countByRevokedAtIsNull()).thenReturn(19L);
        when(eventLogRepository.countErrorsSince(any())).thenReturn(4L);
        when(eventLogRepository.countSecurityEventsSince(any())).thenReturn(6L);

        OpsDtos.PlatformOverview overview = service.overview();

        assertEquals(12L, overview.tenants().total());
        assertEquals(2L, overview.tenants().createdLast30Days());
        assertEquals(40L, overview.accounts().total());
        assertEquals(1L, overview.accounts().lockedOut());
        assertEquals(2L, overview.accounts().platformAdmins());
        assertEquals(7L, overview.queues().mailPending());
        assertEquals(7L, overview.queues().mailFailed());
        assertEquals(19L, overview.queues().activeSessions());
        assertEquals(4L, overview.activity().errorsLast24h());
        assertEquals(6L, overview.activity().securityEventsLast24h());
    }

    @Test
    void listTenantsPassesNoStatusFilterWhenNoneAsked() {
        when(organizationRepository.listWithCursor(isNull(), any(), any(), anyInt()))
                .thenReturn(List.of(organization("Acme", OrganizationStatus.ACTIVE)));

        CursorPage<OpsDtos.TenantSummary> page = service.listTenants("  ", null, null);

        assertEquals(1, page.items().size());
        assertEquals("Acme", page.items().get(0).name());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
    }

    @Test
    void listTenantsNormalizesTheStatusFilter() {
        when(organizationRepository.listWithCursor(eq("SUSPENDED"), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.listTenants("suspended", null, null);

        verify(organizationRepository).listWithCursor(eq("SUSPENDED"), any(), any(), anyInt());
    }

    /**
     * A typo would otherwise return an empty page, which reads as "no tenants are suspended"
     * rather than "you asked for a state that does not exist".
     */
    @Test
    void listTenantsRejectsAnUnknownStatus() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.listTenants("banished", null, null));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    void listTenantsRejectsAMalformedCursor() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.listTenants(null, "not-a-cursor", null));

        assertEquals(ErrorCode.INVALID_CURSOR, ex.getCode());
    }

    private Organization organization(String name, OrganizationStatus status) {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName(name);
        org.setSlug(name.toLowerCase());
        org.setStatus(status);
        org.setCreatedAt(Instant.now());
        return org;
    }
}
