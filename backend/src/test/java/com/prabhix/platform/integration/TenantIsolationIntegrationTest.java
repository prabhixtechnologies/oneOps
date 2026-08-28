package com.prabhix.platform.integration;

import com.prabhix.platform.commerce.domain.CommerceCustomer;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import com.prabhix.platform.security.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
public class TenantIsolationIntegrationTest extends IntegrationTestBase {

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    CommerceCustomerRepository customerRepository;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void tenantScopedReadsReturnOnlyActiveOrganizationRows() {
        Organization orgA = organizationRepository.save(organization("tenant-a-" + UUID.randomUUID()));
        Organization orgB = organizationRepository.save(organization("tenant-b-" + UUID.randomUUID()));

        TenantContext.clear();
        customerRepository.save(customer(orgA.getId(), "alpha@example.com"));
        customerRepository.save(customer(orgB.getId(), "beta@example.com"));

        List<CommerceCustomer> visibleAsA = TenantContext.callAs(orgA.getId(), customerRepository::findAll);
        assertThat(visibleAsA)
                .hasSize(1)
                .first()
                .extracting(CommerceCustomer::getOrganizationId, CommerceCustomer::getEmail)
                .containsExactly(orgA.getId(), "alpha@example.com");

        List<CommerceCustomer> visibleAsB = TenantContext.callAs(orgB.getId(), customerRepository::findAll);
        assertThat(visibleAsB)
                .hasSize(1)
                .first()
                .extracting(CommerceCustomer::getOrganizationId, CommerceCustomer::getEmail)
                .containsExactly(orgB.getId(), "beta@example.com");
    }

    private static Organization organization(String slug) {
        Organization organization = new Organization();
        organization.setName("Org " + slug);
        organization.setSlug(slug);
        organization.setMemberCount(1);
        return organization;
    }

    private static CommerceCustomer customer(UUID organizationId, String email) {
        CommerceCustomer customer = new CommerceCustomer();
        customer.setOrganizationId(organizationId);
        customer.setEmail(email);
        customer.setMarketingConsent(false);
        return customer;
    }
}
