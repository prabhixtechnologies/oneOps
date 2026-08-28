package com.prabhix.platform.observability.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.domain.EventLog;
import com.prabhix.platform.observability.dto.EventLogDtos.EventLogView;
import com.prabhix.platform.observability.repository.EventLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventLogQueryServiceTest {

    @Mock
    EventLogRepository eventLogRepository;
    @Mock
    EntityManager entityManager;

    EventLogQueryService service;

    final UUID orgA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    final UUID orgB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        PrabhixProperties props = new PrabhixProperties(null, null, null, null, null, null, null, null);
        service = new EventLogQueryService(eventLogRepository, entityManager, props);
    }

    @Test
    void tenantIsolationBlocksCrossOrgQuery() {
        assertThrows(ApiException.class, () -> service.search(
                orgA, false, orgB,
                null, null, null, null, null, null, null,
                null, null, null, null, 25));
    }

    @Test
    void platformAdminMayQueryOtherOrg() {
        EventLog row = sampleRow(orgB);
        when(eventLogRepository.findPage(
                eq(orgB), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(row));

        CursorPage<EventLogView> page = service.search(
                orgA, true, orgB,
                null, null, null, null, null, null, null,
                null, null, null, null, 25);

        assertEquals(1, page.items().size());
        assertEquals(orgB, page.items().get(0).organizationId());
    }

    @Test
    void getByIdEnforcesOrg() {
        UUID id = UUID.randomUUID();
        when(eventLogRepository.findByOrgAndId(orgA, id)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.getById(orgA, false, null, id));
    }

    @Test
    void cursorPaginationUsesLimitPlusOne() {
        EventLog first = sampleRow(orgA);
        EventLog second = sampleRow(orgA);
        when(eventLogRepository.findPage(
                eq(orgA), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        CursorPage<EventLogView> page = service.search(
                orgA, false, null,
                null, null, null, null, null, null, null,
                null, null, null, null, 1);

        assertEquals(1, page.items().size());
        assertTrue(page.hasMore());
    }

    private EventLog sampleRow(UUID orgId) {
        EventLog log = new EventLog();
        EventLog.EventLogId pk = new EventLog.EventLogId();
        pk.setId(UUID.randomUUID());
        pk.setOccurredAt(Instant.now());
        log.setId(pk);
        log.setOrganizationId(orgId);
        log.setEventCode("auth.login.failed");
        log.setCategory("AUTH");
        log.setSeverity("WARN");
        log.setCorrelationId("abc123456789");
        return log;
    }
}
