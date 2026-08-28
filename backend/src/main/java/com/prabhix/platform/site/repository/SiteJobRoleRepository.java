package com.prabhix.platform.site.repository;

import com.prabhix.platform.site.domain.SiteEnums;
import com.prabhix.platform.site.domain.SiteJobRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteJobRoleRepository extends JpaRepository<SiteJobRole, UUID> {

    List<SiteJobRole> findByStatusOrderByPublishedAtDesc(SiteEnums.JobRoleStatus status);

    Optional<SiteJobRole> findBySlugAndStatus(String slug, SiteEnums.JobRoleStatus status);
}
