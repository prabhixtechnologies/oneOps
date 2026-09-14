package com.prabhix.platform.visitor.service;

import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Permission;
import com.prabhix.platform.visitor.domain.Visitor;
import com.prabhix.platform.visitor.domain.VisitorEnums;
import com.prabhix.platform.visitor.dto.VisitorDtos;
import com.prabhix.platform.visitor.repository.VisitorDailyAggregateRepository;
import com.prabhix.platform.visitor.repository.VisitorEventRepository;
import com.prabhix.platform.visitor.repository.VisitorPageViewRepository;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import com.prabhix.platform.visitor.repository.VisitorSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorQueryServiceTest {

    @Mock private VisitorRepository visitorRepository;
    @Mock private VisitorSessionRepository sessionRepository;
    @Mock private VisitorPageViewRepository pageViewRepository;
    @Mock private VisitorEventRepository eventRepository;
    @Mock private VisitorPresenceService presenceService;
    @Mock private VisitorDailyAggregateRepository dailyAggregateRepository;
    @Mock private ApplicationEventPublisher events;

    private VisitorQueryService queryService;

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PrabhixProperties properties = new PrabhixProperties(
                null, null, null, null, null, null,
                new PrabhixProperties.Limits(100000, 200, 26214400L, 25, 200));
        queryService = new VisitorQueryService(
                visitorRepository, sessionRepository, pageViewRepository, eventRepository,
                presenceService, dailyAggregateRepository, properties, events);
    }

    @Test
    void crossTenantVisitorLookupFails() {
        UUID visitorId = UUID.randomUUID();
        when(visitorRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(visitorId, orgA))
                .thenReturn(Optional.empty());

        PrabhixPrincipal principal = principal(orgA);
        org.junit.jupiter.api.Assertions.assertThrows(
                com.prabhix.platform.common.error.ApiException.class,
                () -> queryService.get(principal, visitorId));
    }

    @Test
    void cursorPaginationUsesExtraRowForHasMore() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant t = Instant.now();

        Visitor v1 = visitor(id1, orgA, t);
        Visitor v2 = visitor(id2, orgA, t.minusSeconds(10));

        when(visitorRepository.listWithCursor(eq(orgA), any(), any(), eq(3)))
                .thenReturn(List.of(v1, v2, visitor(UUID.randomUUID(), orgA, t.minusSeconds(20))));

        CursorPage<VisitorDtos.VisitorSummary> page = queryService.list(principal(orgA), null, 2);

        assertEquals(2, page.items().size());
        assertFalse(page.items().isEmpty());
    }

    private Visitor visitor(UUID id, UUID orgId, Instant lastSeen) {
        Visitor v = new Visitor();
        v.setId(id);
        v.setOrganizationId(orgId);
        v.setExternalKey("k-" + id);
        v.setConsentStatus(VisitorEnums.ConsentStatus.FULL);
        v.setFirstSeenAt(lastSeen);
        v.setLastSeenAt(lastSeen);
        return v;
    }

    private PrabhixPrincipal principal(UUID orgId) {
        return new PrabhixPrincipal(
                UUID.randomUUID(), "agent@example.com", "Agent",
                orgId, Set.of(Permission.VISITOR_READ), UUID.randomUUID(), false);
    }
}
