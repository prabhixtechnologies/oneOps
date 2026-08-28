package com.prabhix.platform.site.dto;

import com.prabhix.platform.site.domain.SiteEnums;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SiteAdminDtos {

    private SiteAdminDtos() {
    }

    @Schema(name = "SiteLeadSummary")
    public record LeadSummary(
            UUID id,
            String name,
            String email,
            String company,
            SiteEnums.LeadInterest interest,
            String interestRaw,
            SiteEnums.LeadStatus status,
            String source,
            Instant createdAt) {
    }

    @Schema(name = "SiteLeadDetail")
    public record LeadDetail(
            UUID id,
            String name,
            String email,
            String company,
            String phone,
            String employeeCount,
            SiteEnums.LeadInterest interest,
            String interestRaw,
            String message,
            String source,
            Map<String, Object> utm,
            String referrer,
            SiteEnums.LeadStatus status,
            UUID assignedTo,
            String internalNotes,
            Instant contactedAt,
            Instant createdAt) {
    }

    public record UpdateLeadStatusRequest(SiteEnums.LeadStatus status, String internalNotes) {
    }

    @Schema(name = "SiteSubscriberSummary")
    public record SubscriberSummary(
            UUID id,
            String email,
            String name,
            SiteEnums.SubscriberStatus status,
            String source,
            Instant confirmedAt,
            Instant createdAt) {
    }

    @Schema(name = "SiteApplicationSummary")
    public record ApplicationSummary(
            UUID id,
            String roleSlug,
            String name,
            String email,
            SiteEnums.ApplicationStatus status,
            Instant createdAt) {
    }

    @Schema(name = "SiteApplicationDetail")
    public record ApplicationDetail(
            UUID id,
            String roleSlug,
            String name,
            String email,
            String phone,
            String portfolioUrl,
            String linkedinUrl,
            String coverLetter,
            UUID resumeFileId,
            SiteEnums.ApplicationStatus status,
            String internalNotes,
            Instant createdAt) {
    }

    public record UpdateApplicationStatusRequest(SiteEnums.ApplicationStatus status, String internalNotes) {
    }
}
