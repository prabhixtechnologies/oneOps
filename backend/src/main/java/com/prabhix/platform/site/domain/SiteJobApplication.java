package com.prabhix.platform.site.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "site_job_applications")
public class SiteJobApplication extends AuditableEntity {

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "role_slug", nullable = false, length = 120)
    private String roleSlug;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "cover_letter", length = 8000)
    private String coverLetter;

    @Column(name = "resume_file_id")
    private UUID resumeFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SiteEnums.ApplicationStatus status = SiteEnums.ApplicationStatus.RECEIVED;

    @Column(name = "internal_notes", length = 4000)
    private String internalNotes;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
