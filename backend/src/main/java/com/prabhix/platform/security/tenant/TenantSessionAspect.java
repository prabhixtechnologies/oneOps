package com.prabhix.platform.security.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Applies the Hibernate organization filter to the current persistence context.
 *
 * @see TenantFilterConfig for the primary activation path at transaction boundaries
 */
@Component
public class TenantSessionAspect {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Enables the filter for the active transaction, if there is one and a tenant is set.
     */
    public void applyToCurrentSession() {
        if (!TenantContext.isSet() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TenantFilterEnabler.apply(entityManager.unwrap(Session.class));
    }
}
