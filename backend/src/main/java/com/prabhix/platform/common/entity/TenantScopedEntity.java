package com.prabhix.platform.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Base class for every entity that belongs to exactly one customer organization.
 *
 * <p>The {@code organizationFilter} is enabled per session by
 * {@code TenantSessionConfigurer}, which appends {@code organization_id = :organizationId}
 * to every select against these tables. That makes tenant isolation the default rather
 * than something each query has to remember, so a forgotten {@code where} clause leaks
 * nothing.
 *
 * <p>The filter is deliberately <em>not</em> a substitute for checking membership. It only
 * narrows rows to the active organization; whether the caller may act on those rows is a
 * permission question answered by {@code security.rbac}.
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(
        name = TenantScopedEntity.FILTER_NAME,
        parameters = @ParamDef(name = TenantScopedEntity.FILTER_PARAM, type = UUID.class))
@Filter(name = TenantScopedEntity.FILTER_NAME,
        condition = "organization_id = :organizationId")
public abstract class TenantScopedEntity extends AuditableEntity {

    public static final String FILTER_NAME = "organizationFilter";
    public static final String FILTER_PARAM = "organizationId";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
}
