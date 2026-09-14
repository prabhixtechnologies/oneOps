package com.prabhix.platform.observability.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.observability.domain.EventLog;
import com.prabhix.platform.observability.dto.EventLogDtos.EventLogView;
import com.prabhix.platform.observability.repository.EventLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        PrabhixProperties props = new PrabhixProperties(null, null, null, null, null, null, null);
        service = new EventLogQueryService(eventLogRepository, entityManager, props);
    }

    /** Keeps the long positional argument list in one place. */
    private CursorPage<EventLogView> search(UUID tokenOrg,
                                            boolean platformAdmin,
                                            UUID requestedOrg,
                                            boolean allOrganizations,
                                            int limit) {
        return service.search(tokenOrg, platformAdmin, requestedOrg,
                null, null, null, null, null, null, null,
                null, null, null, allOrganizations, null, limit);
    }

    private void expectPageFor(UUID scope, List<EventLog> rows) {
        when(eventLogRepository.findPage(
                eq(scope), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(rows);
    }

    @Test
    void tenantIsolationBlocksCrossOrgQuery() {
        assertThrows(ApiException.class, () -> search(orgA, false, orgB, false, 25));
    }

    @Test
    void platformAdminMayQueryOtherOrg() {
        expectPageFor(orgB, List.of(sampleRow(orgB)));

        CursorPage<EventLogView> page = search(orgA, true, orgB, false, 25);

        assertEquals(1, page.items().size());
        assertEquals(orgB, page.items().get(0).organizationId());
    }

    @Test
    @DisplayName("staff can sweep every organization at once")
    void platformAdminMaySearchAllOrganizations() {
        // A null scope means "no organization filter" in the repository. It was unreachable before:
        // staff tokens carry their own organization, so omitting the parameter narrowed to that
        // rather than widening, and there was no way to ask where errors were coming from without
        // already knowing which tenant to ask.
        expectPageFor(null, List.of(sampleRow(orgA), sampleRow(orgB)));

        CursorPage<EventLogView> page = search(orgA, true, null, true, 25);

        assertThat(page.items()).extracting(EventLogView::organizationId)
                .containsExactly(orgA, orgB);
    }

    @Test
    @DisplayName("a customer asking for every organization is refused, not quietly narrowed")
    void nonAdminCannotSearchAllOrganizations() {
        // Narrowing silently would show them their own rows under the heading "all organizations",
        // which is a wrong answer rather than a denied one.
        ApiException ex = assertThrows(ApiException.class, () -> search(orgA, false, null, true, 25));

        assertEquals(ErrorCode.CROSS_TENANT_ACCESS, ex.getCode());
    }

    @Test
    @DisplayName("naming one organization and all of them at once is contradictory")
    void oneOrgAndAllOrgsTogetherIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> search(orgA, true, orgB, true, 25));

        assertEquals(ErrorCode.MALFORMED_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("staff omitting both still see only their own organization")
    void adminWithoutEitherStaysScopedToTheirOwnOrg() {
        // The widening has to be asked for. Defaulting to every organization would mean a platform
        // admin opening the ordinary logs page saw other tenants' data without intending to.
        expectPageFor(orgA, List.of(sampleRow(orgA)));

        CursorPage<EventLogView> page = search(orgA, true, null, false, 25);

        assertEquals(1, page.items().size());
        assertEquals(orgA, page.items().get(0).organizationId());
    }

    @Test
    void getByIdEnforcesOrg() {
        UUID id = UUID.randomUUID();
        when(eventLogRepository.findByOrgAndId(orgA, id)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.getById(orgA, false, null, false, id));
    }

    @Test
    void cursorPaginationUsesLimitPlusOne() {
        expectPageFor(orgA, List.of(sampleRow(orgA), sampleRow(orgA)));

        CursorPage<EventLogView> page = search(orgA, false, null, false, 1);

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
