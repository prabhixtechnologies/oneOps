package com.prabhix.platform.visitor.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.security.tenant.TenantContext;
import com.prabhix.platform.visitor.config.VisitorProperties;
import com.prabhix.platform.visitor.domain.Visitor;
import com.prabhix.platform.visitor.domain.VisitorEnums;
import com.prabhix.platform.visitor.domain.VisitorEvent;
import com.prabhix.platform.visitor.domain.VisitorPageView;
import com.prabhix.platform.visitor.domain.VisitorSession;
import com.prabhix.platform.visitor.dto.VisitorDtos;
import com.prabhix.platform.visitor.repository.VisitorEventRepository;
import com.prabhix.platform.visitor.repository.VisitorPageViewRepository;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import com.prabhix.platform.visitor.repository.VisitorSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitorIngestService {

    private final OrganizationRepository organizationRepository;
    private final VisitorRepository visitorRepository;
    private final VisitorSessionRepository sessionRepository;
    private final VisitorPageViewRepository pageViewRepository;
    private final VisitorEventRepository eventRepository;
    private final VisitorRateLimiter rateLimiter;
    private final VisitorBotFilter botFilter;
    private final VisitorPresenceService presenceService;
    private final VisitorStitchService stitchService;
    private final VisitorProperties properties;

    public Organization resolveOrg(String orgSlug) {
        return organizationRepository.findBySlug(orgSlug)
                .orElseThrow(() -> ApiException.notFound("Organization"));
    }

    @Transactional
    public VisitorDtos.IngestAck ingest(String orgSlug, VisitorDtos.BatchIngestRequest request,
                                        String ipAddress, String userAgent) {
        if (botFilter.isBot(userAgent)) {
            return new VisitorDtos.IngestAck(request.visitorKey(), request.sessionId());
        }

        rateLimiter.checkIp(ipAddress);
        rateLimiter.checkVisitor(request.visitorKey());

        int eventCount = (request.pageViews() == null ? 0 : request.pageViews().size())
                + (request.events() == null ? 0 : request.events().size());
        if (eventCount > properties.ingestBatchMaxEvents()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Batch exceeds maximum event count");
        }

        Organization org = resolveOrg(orgSlug);
        return TenantContext.callAs(org.getId(), () -> doIngest(org.getId(), request, ipAddress));
    }

    private VisitorDtos.IngestAck doIngest(UUID orgId, VisitorDtos.BatchIngestRequest request, String ipAddress) {
        final String externalKey = (request.visitorKey() == null || request.visitorKey().isBlank())
                ? stitchService.issueExternalKey()
                : request.visitorKey();

        Visitor visitor = visitorRepository
                .findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(orgId, externalKey)
                .orElseGet(() -> {
                    Visitor created = new Visitor();
                    created.setOrganizationId(orgId);
                    created.setExternalKey(externalKey);
                    created.setConsentStatus(request.consent() == null
                            ? VisitorEnums.ConsentStatus.FULL : request.consent());
                    created.setFirstSeenAt(Instant.now());
                    created.setLastSeenAt(Instant.now());
                    if (request.utm() != null && !request.utm().isEmpty()) {
                        created.setFirstTouchUtm(request.utm());
                    }
                    created.setFirstTouchReferrer(request.referrer());
                    return visitorRepository.save(created);
                });

        if (visitor.getConsentStatus() == VisitorEnums.ConsentStatus.DELETED) {
            return new VisitorDtos.IngestAck(externalKey, request.sessionId());
        }

        visitor.setConsentStatus(request.consent() == null ? visitor.getConsentStatus() : request.consent());
        visitor.setLastSeenAt(Instant.now());
        visitorRepository.save(visitor);

        VisitorSession session = resolveSession(orgId, visitor, request, ipAddress);

        if (visitor.getConsentStatus() == VisitorEnums.ConsentStatus.FULL) {
            persistPageViews(orgId, visitor, session, request.pageViews());
            persistEvents(orgId, visitor, session, request.events());
        }

        if (request.presence() != null) {
            presenceService.touch(orgId, visitor.getId(), externalKey, request.presence(),
                    visitor.getEmail(), visitor.getDisplayName());
        }

        return new VisitorDtos.IngestAck(externalKey, session.getId());
    }

    private VisitorSession resolveSession(UUID orgId, Visitor visitor,
                                          VisitorDtos.BatchIngestRequest request, String ipAddress) {
        if (request.sessionId() != null) {
            return sessionRepository.findByIdAndOrganizationId(request.sessionId(), orgId)
                    .orElseGet(() -> createSession(orgId, visitor, request, ipAddress));
        }
        return createSession(orgId, visitor, request, ipAddress);
    }

    private VisitorSession createSession(UUID orgId, Visitor visitor,
                                         VisitorDtos.BatchIngestRequest request, String ipAddress) {
        VisitorSession session = new VisitorSession();
        session.setOrganizationId(orgId);
        session.setVisitorId(visitor.getId());
        session.setIpAddress(ipAddress);
        if (request.session() != null) {
            VisitorDtos.SessionContext ctx = request.session();
            session.setDeviceType(ctx.deviceType());
            session.setBrowser(ctx.browser());
            session.setOs(ctx.os());
            session.setScreenWidth(ctx.screenWidth());
            session.setScreenHeight(ctx.screenHeight());
            session.setLanguage(ctx.language());
            session.setTimezone(ctx.timezone());
        }
        if (request.utm() != null) {
            session.setUtm(request.utm());
        }
        session.setReferrer(request.referrer());
        if (request.pageViews() != null && !request.pageViews().isEmpty()) {
            session.setEntryUrl(request.pageViews().get(0).url());
        }
        return sessionRepository.save(session);
    }

    private void persistPageViews(UUID orgId, Visitor visitor, VisitorSession session,
                                    List<VisitorDtos.PageViewInput> pageViews) {
        if (pageViews == null || pageViews.isEmpty()) {
            return;
        }
        for (VisitorDtos.PageViewInput input : pageViews) {
            VisitorPageView view = new VisitorPageView();
            view.setOrganizationId(orgId);
            view.setVisitorId(visitor.getId());
            view.setSessionId(session.getId());
            view.setUrl(input.url());
            view.setPath(input.path());
            view.setTitle(input.title());
            view.setReferrer(input.referrer());
            view.setDurationMs(input.durationMs());
            view.setEntry(input.entry());
            view.setExit(input.exit());
            pageViewRepository.save(view);
        }
    }

    private void persistEvents(UUID orgId, Visitor visitor, VisitorSession session,
                               List<VisitorDtos.CustomEventInput> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (VisitorDtos.CustomEventInput input : events) {
            VisitorEvent event = new VisitorEvent();
            event.setOrganizationId(orgId);
            event.setVisitorId(visitor.getId());
            event.setSessionId(session.getId());
            event.setEventName(input.name());
            event.setProperties(input.properties() == null ? Map.of() : input.properties());
            eventRepository.save(event);
        }
    }

    @Transactional
    public void identify(String orgSlug, VisitorDtos.IdentifyRequest request) {
        Organization org = resolveOrg(orgSlug);
        TenantContext.runAs(org.getId(), () ->
                stitchService.identify(org.getId(), request.visitorKey(),
                        request.email(), request.name(), request.userId()));
    }
}
