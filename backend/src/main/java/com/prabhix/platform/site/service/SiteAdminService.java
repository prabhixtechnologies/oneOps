package com.prabhix.platform.site.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.site.domain.SiteEnums;
import com.prabhix.platform.site.domain.SiteJobApplication;
import com.prabhix.platform.site.domain.SiteLead;
import com.prabhix.platform.site.domain.SiteSubscriber;
import com.prabhix.platform.site.dto.SiteAdminDtos;
import com.prabhix.platform.site.repository.SiteJobApplicationRepository;
import com.prabhix.platform.site.repository.SiteLeadRepository;
import com.prabhix.platform.site.repository.SiteSubscriberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SiteAdminService {

    private final SiteLeadRepository leadRepository;
    private final SiteSubscriberRepository subscriberRepository;
    private final SiteJobApplicationRepository applicationRepository;
    private final PrabhixProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public CursorPage<SiteAdminDtos.LeadSummary> listLeads(String status, String cursor, Integer limit) {
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<SiteLead> fetched = leadRepository.listWithCursor(
                blankToNull(status), decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toLeadSummary,
                l -> Cursor.of(l.getCreatedAt(), l.getId()).encode());
    }

    @Transactional(readOnly = true)
    public SiteAdminDtos.LeadDetail getLead(UUID id) {
        return toLeadDetail(requireLead(id));
    }

    @Transactional
    public SiteAdminDtos.LeadDetail updateLeadStatus(UUID id, UUID actorId,
                                                     SiteAdminDtos.UpdateLeadStatusRequest request) {
        SiteLead lead = requireLead(id);
        lead.setStatus(request.status());
        if (request.internalNotes() != null) {
            lead.setInternalNotes(request.internalNotes());
        }
        if (request.status() == SiteEnums.LeadStatus.CONTACTED && lead.getContactedAt() == null) {
            lead.setContactedAt(Instant.now());
        }
        lead = leadRepository.save(lead);
        events.publishEvent(AuditRequested.changed(null, actorId,
                "site.lead.status_updated", "site_lead", id,
                Map.of("status", request.status().name())));
        return toLeadDetail(lead);
    }

    @Transactional(readOnly = true)
    public CursorPage<SiteAdminDtos.SubscriberSummary> listSubscribers(String status, String cursor,
                                                                       Integer limit) {
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<SiteSubscriber> fetched = subscriberRepository.listWithCursor(
                blankToNull(status), decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toSubscriberSummary,
                s -> Cursor.of(s.getCreatedAt(), s.getId()).encode());
    }

    @Transactional(readOnly = true)
    public SiteAdminDtos.SubscriberSummary getSubscriber(UUID id) {
        return toSubscriberSummary(requireSubscriber(id));
    }

    @Transactional(readOnly = true)
    public CursorPage<SiteAdminDtos.ApplicationSummary> listApplications(String status, String cursor,
                                                                       Integer limit) {
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<SiteJobApplication> fetched = applicationRepository.listWithCursor(
                blankToNull(status), decoded.timestamp(), decoded.id(), pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toApplicationSummary,
                a -> Cursor.of(a.getCreatedAt(), a.getId()).encode());
    }

    @Transactional(readOnly = true)
    public SiteAdminDtos.ApplicationDetail getApplication(UUID id) {
        return toApplicationDetail(requireApplication(id));
    }

    @Transactional
    public SiteAdminDtos.ApplicationDetail updateApplicationStatus(UUID id, UUID actorId,
                                                                   SiteAdminDtos.UpdateApplicationStatusRequest request) {
        SiteJobApplication application = requireApplication(id);
        application.setStatus(request.status());
        if (request.internalNotes() != null) {
            application.setInternalNotes(request.internalNotes());
        }
        application = applicationRepository.save(application);
        events.publishEvent(AuditRequested.changed(null, actorId,
                "site.application.status_updated", "site_job_application", id,
                Map.of("status", request.status().name())));
        return toApplicationDetail(application);
    }

    private SiteLead requireLead(UUID id) {
        return leadRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lead"));
    }

    private SiteSubscriber requireSubscriber(UUID id) {
        return subscriberRepository.findById(id).orElseThrow(() -> ApiException.notFound("Subscriber"));
    }

    private SiteJobApplication requireApplication(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Application"));
    }

    private SiteAdminDtos.LeadSummary toLeadSummary(SiteLead lead) {
        return new SiteAdminDtos.LeadSummary(
                lead.getId(), lead.getName(), lead.getEmail(), lead.getCompany(),
                lead.getInterest(), lead.getInterestRaw(), lead.getStatus(),
                lead.getSource(), lead.getCreatedAt());
    }

    private SiteAdminDtos.LeadDetail toLeadDetail(SiteLead lead) {
        return new SiteAdminDtos.LeadDetail(
                lead.getId(), lead.getName(), lead.getEmail(), lead.getCompany(),
                lead.getPhone(), lead.getEmployeeCount(), lead.getInterest(), lead.getInterestRaw(),
                lead.getMessage(), lead.getSource(), lead.getUtm(), lead.getReferrer(),
                lead.getStatus(), lead.getAssignedTo(), lead.getInternalNotes(),
                lead.getContactedAt(), lead.getCreatedAt());
    }

    private SiteAdminDtos.SubscriberSummary toSubscriberSummary(SiteSubscriber subscriber) {
        return new SiteAdminDtos.SubscriberSummary(
                subscriber.getId(), subscriber.getEmail(), subscriber.getName(),
                subscriber.getStatus(), subscriber.getSource(),
                subscriber.getConfirmedAt(), subscriber.getCreatedAt());
    }

    private SiteAdminDtos.ApplicationSummary toApplicationSummary(SiteJobApplication application) {
        return new SiteAdminDtos.ApplicationSummary(
                application.getId(), application.getRoleSlug(), application.getName(),
                application.getEmail(), application.getStatus(), application.getCreatedAt());
    }

    private SiteAdminDtos.ApplicationDetail toApplicationDetail(SiteJobApplication application) {
        return new SiteAdminDtos.ApplicationDetail(
                application.getId(), application.getRoleSlug(), application.getName(),
                application.getEmail(), application.getPhone(), application.getPortfolioUrl(),
                application.getLinkedinUrl(), application.getCoverLetter(),
                application.getResumeFileId(), application.getStatus(),
                application.getInternalNotes(), application.getCreatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
