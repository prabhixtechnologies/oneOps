package com.prabhix.platform.site.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "site_job_roles")
public class SiteJobRole extends AuditableEntity {

    @Column(name = "slug", nullable = false, length = 120, unique = true)
    private String slug;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "department", nullable = false, length = 120)
    private String department;

    @Column(name = "location", nullable = false, length = 160)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 24)
    private SiteEnums.JobEmploymentType employmentType = SiteEnums.JobEmploymentType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 16)
    private SiteEnums.JobWorkMode workMode = SiteEnums.JobWorkMode.HYBRID;

    @Column(name = "experience_range", length = 60)
    private String experienceRange;

    @Column(name = "salary_range", length = 80)
    private String salaryRange;

    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    @Column(name = "description_md", nullable = false, columnDefinition = "text")
    private String descriptionMd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SiteEnums.JobRoleStatus status = SiteEnums.JobRoleStatus.OPEN;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
