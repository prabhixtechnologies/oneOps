package com.prabhix.platform.site.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.common.mail.MailRequest;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.security.tenant.TenantContext;
import com.prabhix.platform.site.domain.SiteEnums;
import com.prabhix.platform.site.domain.SiteJobApplication;
import com.prabhix.platform.site.domain.SiteJobRole;
import com.prabhix.platform.site.domain.SiteLead;
import com.prabhix.platform.site.domain.SiteSubscriber;
import com.prabhix.platform.site.dto.SiteDtos.ApplicationAck;
import com.prabhix.platform.site.dto.SiteDtos.GenericAck;
import com.prabhix.platform.site.dto.SiteDtos.JobApplicationRequest;
import com.prabhix.platform.site.dto.SiteDtos.JobRoleDetail;
import com.prabhix.platform.site.dto.SiteDtos.JobRoleSummary;
import com.prabhix.platform.site.dto.SiteDtos.LeadRequest;
import com.prabhix.platform.site.dto.SiteDtos.SubscribeRequest;
import com.prabhix.platform.site.event.LeadSubmitted;
import com.prabhix.platform.site.service.LeadInterestParser;
import com.prabhix.platform.site.repository.SiteJobApplicationRepository;
import com.prabhix.platform.site.repository.SiteJobRoleRepository;
import com.prabhix.platform.site.repository.SiteLeadRepository;
import com.prabhix.platform.site.repository.SiteSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private static final int LEAD_RATE_LIMIT_PER_HOUR = 5;

    private final SiteLeadRepository leadRepository;
    private final SiteSubscriberRepository subscriberRepository;
    private final SiteJobRoleRepository jobRoleRepository;
    private final SiteJobApplicationRepository applicationRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher events;
    private final MailClient mail;
    private final PrabhixProperties properties;

    @Value("${prabhix.site.internal-organization-id:}")
    private String internalOrganizationId;

    @Transactional
    public GenericAck submitLead(LeadRequest request, String ipAddress, String userAgent) {
        if (request.website() != null && !request.website().isBlank()) {
            return GenericAck.ok();
        }

        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        if (leadRepository.countRecentByEmail(request.email().toLowerCase(), since) >= LEAD_RATE_LIMIT_PER_HOUR) {
            throw ApiException.of(ErrorCode.RATE_LIMITED, "Too many submissions. Try again later.");
        }
        if (ipAddress != null
                && leadRepository.countRecentByIp(ipAddress, since) >= LEAD_RATE_LIMIT_PER_HOUR) {
            throw ApiException.of(ErrorCode.RATE_LIMITED, "Too many submissions. Try again later.");
        }

        SiteLead lead = new SiteLead();
        lead.setName(request.name());
        lead.setEmail(request.email().toLowerCase());
        lead.setCompany(request.company());
        lead.setPhone(request.phone());
        lead.setEmployeeCount(request.employeeCount());
        LeadInterestParser.ParsedInterest parsedInterest = LeadInterestParser.parse(request.interest());
        lead.setInterest(parsedInterest.canonical());
        lead.setInterestRaw(parsedInterest.rawLabel());
        lead.setMessage(request.message());
        lead.setSource(request.source() == null ? "contact-form" : request.source());
        lead.setUtm(request.utm() == null ? Map.of() : request.utm());
        lead.setReferrer(request.referrer());
        lead.setIpAddress(ipAddress);
        lead.setUserAgent(userAgent);
        lead = leadRepository.save(lead);

        mail.send(MailRequest.to(
                lead.getEmail(),
                "site.lead-acknowledgement",
                Map.of("name", lead.getName(), "message", lead.getMessage())));
        events.publishEvent(new LeadSubmitted(lead.getId(), lead.getEmail(), lead.getName()));

        return GenericAck.ok();
    }

    @Transactional
    public GenericAck subscribe(SubscribeRequest request, String ipAddress) {
        String email = request.email().toLowerCase();
        subscriberRepository.findByEmail(email).ifPresent(existing -> {
            // Never reveal whether the address was already subscribed.
        });

        String confirmToken = Ids.token();
        String confirmHash = sha256Hex(confirmToken);
        String unsubscribeToken = Ids.token(16);

        SiteSubscriber subscriber = subscriberRepository.findByEmail(email).orElseGet(SiteSubscriber::new);
        subscriber.setEmail(email);
        subscriber.setName(request.name());
        subscriber.setSource(request.source() == null ? "footer" : request.source());
        subscriber.setStatus(SiteEnums.SubscriberStatus.PENDING);
        subscriber.setConfirmTokenHash(confirmHash);
        subscriber.setUnsubscribeToken(unsubscribeToken);
        subscriber.setIpAddress(ipAddress);
        subscriberRepository.save(subscriber);

        // The confirm endpoint is served by the API, not the marketing site, so this must
        // use the API base URL. Pointing it at the marketing host produces a dead link.
        String confirmUrl = properties.urls().api()
                + "/api/v1/oneops/site/subscribers/confirm?token=" + confirmToken;
        mail.send(MailRequest.to(
                email,
                "site.newsletter-confirm",
                Map.of("confirmUrl", confirmUrl)));

        return GenericAck.ok();
    }

    @Transactional
    public GenericAck confirmSubscription(String token) {
        if (token == null || token.isBlank()) {
            return GenericAck.ok();
        }
        subscriberRepository.findByConfirmTokenHashAndStatus(
                        sha256Hex(token), SiteEnums.SubscriberStatus.PENDING)
                .ifPresent(subscriber -> {
                    subscriber.setStatus(SiteEnums.SubscriberStatus.CONFIRMED);
                    subscriber.setConfirmedAt(Instant.now());
                    subscriber.setConfirmTokenHash(null);
                    subscriberRepository.save(subscriber);
                });
        return GenericAck.ok();
    }

    @Transactional
    public GenericAck unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            return GenericAck.ok();
        }
        subscriberRepository.findByUnsubscribeToken(token).ifPresent(subscriber -> {
            subscriber.setStatus(SiteEnums.SubscriberStatus.UNSUBSCRIBED);
            subscriber.setUnsubscribedAt(Instant.now());
            subscriberRepository.save(subscriber);
        });
        return GenericAck.ok();
    }

    @Transactional(readOnly = true)
    public List<JobRoleSummary> listOpenRoles() {
        return jobRoleRepository.findByStatusOrderByPublishedAtDesc(SiteEnums.JobRoleStatus.OPEN)
                .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public JobRoleDetail getRole(String slug) {
        SiteJobRole role = jobRoleRepository.findBySlugAndStatus(slug, SiteEnums.JobRoleStatus.OPEN)
                .orElseThrow(() -> ApiException.notFound("Role"));
        return toDetail(role);
    }

    @Transactional
    public ApplicationAck submitApplication(JobApplicationRequest request,
                                          MultipartFile resume,
                                          String ipAddress) {
        SiteJobRole role = jobRoleRepository.findBySlugAndStatus(
                        request.roleSlug(), SiteEnums.JobRoleStatus.OPEN)
                .orElseThrow(() -> ApiException.notFound("Role"));

        SiteJobApplication application = applicationRepository
                .findByRoleSlugAndEmail(request.roleSlug(), request.email().toLowerCase())
                .orElseGet(SiteJobApplication::new);

        application.setRoleId(role.getId());
        application.setRoleSlug(role.getSlug());
        application.setName(request.name());
        application.setEmail(request.email().toLowerCase());
        application.setPhone(request.phone());
        application.setPortfolioUrl(request.portfolioUrl());
        application.setLinkedinUrl(request.linkedinUrl());
        application.setCoverLetter(request.coverLetter());
        application.setIpAddress(ipAddress);
        application.setStatus(SiteEnums.ApplicationStatus.RECEIVED);

        if (resume != null && !resume.isEmpty()) {
            storeResume(resume, application);
        }

        applicationRepository.save(application);
        return ApplicationAck.ok();
    }

    /**
     * Keeps the application even when storage is misconfigured: losing a candidate's
     * cover letter and contact details is worse than saving the row without a résumé.
     * Operators see {@code internalNotes} and the warn log; the candidate still gets an ack.
     */
    private void storeResume(MultipartFile resume, SiteJobApplication application) {
        if (internalOrganizationId == null || internalOrganizationId.isBlank()) {
            log.warn("Résumé not stored: prabhix.site.internal-organization-id is not configured");
            application.setInternalNotes(
                    "Résumé not stored: prabhix.site.internal-organization-id is not configured");
            return;
        }
        UUID orgId;
        try {
            orgId = UUID.fromString(internalOrganizationId.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Résumé not stored: prabhix.site.internal-organization-id is not a valid UUID");
            application.setInternalNotes(
                    "Résumé not stored: prabhix.site.internal-organization-id is not a valid UUID");
            return;
        }
        try {
            StoredFile stored = TenantContext.callAs(orgId, () -> {
                try {
                    return fileStorageService.store(
                            resume.getBytes(),
                            resume.getOriginalFilename(),
                            resume.getContentType(),
                            StoredFile.FilePurpose.IMPORT,
                            null);
                } catch (Exception ex) {
                    throw ApiException.of(ErrorCode.STORAGE_ERROR, "Could not store résumé", ex);
                }
            });
            application.setResumeFileId(stored.getId());
        } catch (ApiException ex) {
            log.warn("Résumé not stored for {}: {}", application.getEmail(), ex.getMessage());
            application.setInternalNotes("Résumé not stored: " + ex.getMessage());
        }
    }

    private JobRoleSummary toSummary(SiteJobRole role) {
        return new JobRoleSummary(
                role.getId(), role.getSlug(), role.getTitle(), role.getDepartment(),
                role.getLocation(), role.getEmploymentType(), role.getWorkMode(),
                role.getExperienceRange(), role.getSalaryRange(), role.getSummary(),
                role.getPublishedAt());
    }

    private JobRoleDetail toDetail(SiteJobRole role) {
        return new JobRoleDetail(
                role.getId(), role.getSlug(), role.getTitle(), role.getDepartment(),
                role.getLocation(), role.getEmploymentType(), role.getWorkMode(),
                role.getExperienceRange(), role.getSalaryRange(), role.getSummary(),
                role.getDescriptionMd(), role.getPublishedAt());
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
