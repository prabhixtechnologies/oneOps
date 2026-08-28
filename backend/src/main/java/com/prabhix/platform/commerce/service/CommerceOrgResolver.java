package com.prabhix.platform.commerce.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CommerceOrgResolver {

    private final OrganizationRepository organizationRepository;

    public Organization resolve(String orgSlug) {
        return organizationRepository.findBySlug(orgSlug)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORGANIZATION_NOT_FOUND,
                        "That organization was not found"));
    }

    public UUID resolveId(String orgSlug) {
        return resolve(orgSlug).getId();
    }

    public <T> T runAs(String orgSlug, Supplier<T> work) {
        return TenantContext.callAs(resolveId(orgSlug), work);
    }

    public void runAs(String orgSlug, Runnable work) {
        TenantContext.runAs(resolveId(orgSlug), work);
    }
}
