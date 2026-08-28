package com.prabhix.platform.visitor.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.visitor.domain.Visitor;
import com.prabhix.platform.visitor.domain.VisitorDailyAggregate;
import com.prabhix.platform.visitor.domain.VisitorEnums;
import com.prabhix.platform.visitor.domain.VisitorEvent;
import com.prabhix.platform.visitor.domain.VisitorPageView;
import com.prabhix.platform.visitor.domain.VisitorSession;
import com.prabhix.platform.visitor.dto.VisitorDtos;
import com.prabhix.platform.visitor.repository.VisitorDailyAggregateRepository;
import com.prabhix.platform.visitor.repository.VisitorEventRepository;
import com.prabhix.platform.visitor.repository.VisitorPageViewRepository;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import com.prabhix.platform.visitor.repository.VisitorSessionRepository;
import com.prabhix.platform.visitor.util.VisitorCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitorQueryService {

    private final VisitorRepository visitorRepository;
    private final VisitorSessionRepository sessionRepository;
    private final VisitorPageViewRepository pageViewRepository;
    private final VisitorEventRepository eventRepository;
    private final VisitorPresenceService presenceService;
    private final VisitorDailyAggregateRepository dailyAggregateRepository;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<VisitorDtos.LiveVisitor> live(PrabhixPrincipal principal) {
        return presenceService.listLive(principal.requireOrganizationId());
    }

    @Transactional(readOnly = true)
    public CursorPage<VisitorDtos.VisitorSummary> list(PrabhixPrincipal principal, String cursor, Integer limit) {
        UUID orgId = principal.requireOrganizationId();
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<Visitor> fetched = visitorRepository.listWithCursor(
                orgId, decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toSummary, VisitorCursor::encodeVisitor);
    }

    @Transactional(readOnly = true)
    public VisitorDtos.VisitorDetail get(PrabhixPrincipal principal, UUID visitorId) {
        UUID orgId = principal.requireOrganizationId();
        Visitor visitor = visitorRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(visitorId, orgId)
                .orElseThrow(() -> ApiException.notFound("Visitor"));
        List<VisitorSession> sessions = sessionRepository
                .findByVisitorIdAndOrganizationIdOrderByStartedAtDesc(visitor.getId(), orgId);
        return new VisitorDtos.VisitorDetail(toSummary(visitor),
                sessions.stream().map(this::toSessionView).toList());
    }

    @Transactional(readOnly = true)
    public CursorPage<VisitorDtos.PageViewView> pageViews(PrabhixPrincipal principal, UUID visitorId,
                                                          String cursor, Integer limit) {
        UUID orgId = principal.requireOrganizationId();
        assertVisitor(orgId, visitorId);
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<VisitorPageView> fetched = pageViewRepository.listForVisitorWithCursor(
                orgId, visitorId, decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toPageView, VisitorCursor::encodePageView);
    }

    @Transactional(readOnly = true)
    public CursorPage<VisitorDtos.EventView> events(PrabhixPrincipal principal, UUID visitorId,
                                                    String cursor, Integer limit) {
        UUID orgId = principal.requireOrganizationId();
        assertVisitor(orgId, visitorId);
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<VisitorEvent> fetched = eventRepository.listForVisitorWithCursor(
                orgId, visitorId, decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toEventView, VisitorCursor::encodeEvent);
    }

    @Transactional(readOnly = true)
    public VisitorDtos.AnalyticsSummary analytics(PrabhixPrincipal principal, int days) {
        UUID orgId = principal.requireOrganizationId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        LocalDate fromDate = LocalDate.ofInstant(since, ZoneOffset.UTC);
        LocalDate toDate = LocalDate.now(ZoneOffset.UTC);

        List<Object[]> topPages = pageViewRepository.topPages(orgId, since, 10);

        List<VisitorDailyAggregate> sessionAggs = dailyAggregateRepository.findRange(
                orgId, VisitorEnums.AggregateMetric.SESSIONS.name(), fromDate, toDate);
        List<VisitorDtos.DimensionCount> referrers = sessionAggs.stream()
                .filter(a -> a.getDimension() != null && !a.getDimension().isBlank())
                .sorted((a, b) -> Long.compare(b.getCountValue(), a.getCountValue()))
                .limit(10)
                .map(a -> new VisitorDtos.DimensionCount(a.getDimension(), a.getCountValue()))
                .toList();

        List<VisitorDailyAggregate> visitorAggs = dailyAggregateRepository.findRange(
                orgId, VisitorEnums.AggregateMetric.UNIQUE_VISITORS.name(), fromDate, toDate);
        List<VisitorDtos.TimeSeriesPoint> sessionsOverTime = visitorAggs.stream()
                .filter(a -> a.getDimension() == null || a.getDimension().isBlank())
                .map(a -> new VisitorDtos.TimeSeriesPoint(a.getAggregateDate().toString(), a.getCountValue()))
                .toList();

        List<VisitorDailyAggregate> eventAggs = dailyAggregateRepository.findRange(
                orgId, VisitorEnums.AggregateMetric.EVENT.name(), fromDate, toDate);
        long leadConversions = eventAggs.stream()
                .filter(a -> "lead_submitted".equals(a.getDimension()))
                .mapToLong(VisitorDailyAggregate::getCountValue).sum();
        long chatConversions = eventAggs.stream()
                .filter(a -> "chat_started".equals(a.getDimension()))
                .mapToLong(VisitorDailyAggregate::getCountValue).sum();
        long visitCount = visitorAggs.stream()
                .filter(a -> a.getDimension() == null || a.getDimension().isBlank())
                .mapToLong(VisitorDailyAggregate::getCountValue).sum();

        return new VisitorDtos.AnalyticsSummary(
                topPages.stream()
                        .map(row -> new VisitorDtos.DimensionCount((String) row[0], ((Number) row[1]).longValue()))
                        .toList(),
                referrers,
                sessionsOverTime,
                visitCount,
                leadConversions,
                chatConversions);
    }

    @Transactional
    public void deleteVisitorData(PrabhixPrincipal principal, UUID visitorId) {
        UUID orgId = principal.requireOrganizationId();
        Visitor visitor = visitorRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(visitorId, orgId)
                .orElseThrow(() -> ApiException.notFound("Visitor"));
        visitor.setConsentStatus(VisitorEnums.ConsentStatus.DELETED);
        visitor.setDeletedAt(Instant.now());
        visitor.setEmail(null);
        visitor.setDisplayName(null);
        visitorRepository.save(visitor);
        events.publishEvent(AuditRequested.labelled(orgId, principal.userId(),
                "visitor.deleted", "visitor", visitorId, visitor.getExternalKey()));
    }

    private void assertVisitor(UUID orgId, UUID visitorId) {
        visitorRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(visitorId, orgId)
                .orElseThrow(() -> ApiException.notFound("Visitor"));
    }

    private VisitorDtos.VisitorSummary toSummary(Visitor visitor) {
        return new VisitorDtos.VisitorSummary(
                visitor.getId(),
                visitor.getExternalKey(),
                visitor.getConsentStatus(),
                visitor.getFirstSeenAt(),
                visitor.getLastSeenAt(),
                visitor.getEmail(),
                visitor.getDisplayName(),
                visitor.getEmail() != null || visitor.getIdentifiedUserId() != null);
    }

    private VisitorDtos.SessionView toSessionView(VisitorSession session) {
        return new VisitorDtos.SessionView(
                session.getId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSeconds(),
                session.getEntryUrl(),
                session.getExitUrl(),
                session.getReferrer(),
                session.getDeviceType(),
                session.getBrowser(),
                session.getOs(),
                session.getGeoCountry(),
                session.getGeoCity());
    }

    private VisitorDtos.PageViewView toPageView(VisitorPageView view) {
        return new VisitorDtos.PageViewView(
                view.getId(), view.getUrl(), view.getPath(), view.getTitle(),
                view.getViewedAt(), view.getDurationMs(), view.isEntry(), view.isExit());
    }

    private VisitorDtos.EventView toEventView(VisitorEvent event) {
        return new VisitorDtos.EventView(event.getId(), event.getEventName(),
                event.getProperties(), event.getOccurredAt());
    }
}
