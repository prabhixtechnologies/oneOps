package com.prabhix.platform.security.tenant;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import org.hibernate.Filter;
import org.hibernate.Session;

import java.util.UUID;

/**
 * Enables Hibernate's {@code organizationFilter} on the current session when a tenant is active.
 *
 * <p>Platform admins without {@code X-Prabhix-Org} have no tenant in {@link TenantContext}, so
 * the filter stays off and cross-tenant reads remain possible for staff tooling. When they
 * select an organization via the header, the filter narrows to that org like any other caller.
 *
 * <p>Background schedulers ({@code OutboxWorker}, billing jobs, audit maintenance) run with no
 * tenant set, so the filter is never applied and native cross-tenant queries keep working.
 * Public marketing endpoints write to non-tenant tables ({@code site_leads}, etc.) and are
 * unaffected because those entities do not extend {@link TenantScopedEntity}.
 */
public final class TenantFilterEnabler {

    private TenantFilterEnabler() {
    }

    public static void apply(Session session) {
        if (!TenantContext.isSet()) {
            return;
        }
        UUID organizationId = TenantContext.current().orElseThrow();
        Filter existing = session.getEnabledFilter(TenantScopedEntity.FILTER_NAME);
        if (existing != null) {
            existing.setParameter(TenantScopedEntity.FILTER_PARAM, organizationId);
            return;
        }
        session.enableFilter(TenantScopedEntity.FILTER_NAME)
                .setParameter(TenantScopedEntity.FILTER_PARAM, organizationId);
    }
}
