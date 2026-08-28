package com.prabhix.platform.site.dto;

import com.prabhix.platform.site.domain.SiteEnums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SiteDtos {

    private SiteDtos() {
    }

    public record LeadRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 200) String company,
            @Size(max = 32) String phone,
            @Size(max = 32) String employeeCount,
            String interest,
            @NotBlank @Size(max = 4000) String message,
            @Size(max = 80) String source,
            Map<String, Object> utm,
            @Size(max = 500) String referrer,
            /** Honeypot field — must be blank for legitimate submissions. */
            @Size(max = 0) String website) {
    }

    public record GenericAck(String message) {
        public static GenericAck ok() {
            return new GenericAck("Thank you. We will be in touch shortly.");
        }
    }

    public record SubscribeRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 160) String name,
            @Size(max = 80) String source) {
    }

    public record JobRoleSummary(
            UUID id,
            String slug,
            String title,
            String department,
            String location,
            SiteEnums.JobEmploymentType employmentType,
            SiteEnums.JobWorkMode workMode,
            String experienceRange,
            String salaryRange,
            String summary,
            Instant publishedAt) {
    }

    public record JobRoleDetail(
            UUID id,
            String slug,
            String title,
            String department,
            String location,
            SiteEnums.JobEmploymentType employmentType,
            SiteEnums.JobWorkMode workMode,
            String experienceRange,
            String salaryRange,
            String summary,
            String descriptionMd,
            Instant publishedAt) {
    }

    public record JobApplicationRequest(
            @NotBlank @Size(max = 120) String roleSlug,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 32) String phone,
            @Size(max = 500) String portfolioUrl,
            @Size(max = 500) String linkedinUrl,
            @Size(max = 8000) String coverLetter) {
    }

    public record ApplicationAck(String message) {
        public static ApplicationAck ok() {
            return new ApplicationAck("Your application has been received.");
        }
    }
}
